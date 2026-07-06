# Edge Cases

> **Design document.** Describes the intended design and may run ahead of the current build (0.3.0). See `ROADMAP.md` for what has actually shipped.


The unfun parts of the design. The cases that break naive implementations and where 80% of the project's real complexity hides.

## Background location reliability

### The problem

Android tries hard to kill apps that hold location resources. The kernel-level rules are the same on all devices, but OEMs (especially Samsung, Xiaomi, Huawei, OPPO) layer additional aggressive killing on top. A foreground service that "should" run forever often does not.

### Mitigations

**Foreground service done correctly.** The service must:
- Declare `foregroundServiceType="location"` in the manifest.
- Call `startForeground(id, notification)` within 5 seconds of `startService()`. Failing this kills the service and throws on Android 12+.
- Show a notification that explains what the app is doing ("Kilomètre — tracking trip · 14 km · 23 min").
- Use `android:exported="false"` so other apps can't poke it.

**Battery optimization exemption.** Prompt the user on first session: "To track drives reliably, allow Kilomètre to run in the background." Link to system settings via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Without this, Samsung One UI's "Adaptive Battery" or "Sleep apps" feature will silently kill the service.

**Samsung-specific guidance.** Document for users that on Samsung devices, in addition to the standard prompt, they should:
- Settings → Battery → Background usage limits → Never sleeping apps → add Kilomètre.
- Settings → Apps → Kilomètre → Battery → Unrestricted.

This is annoying, but Samsung's behavior is well-documented at `https://dontkillmyapp.com/samsung`. We can't fix Samsung; we can document the workaround.

**Verification on a low-end device.** The low-end test device is intentionally aggressive in battery killing. If a session survives 60 minutes there, it will survive on most other devices. This is Gate G4.

### What we will NOT do

- Acquire a wake lock. Foreground service notifications already prevent CPU sleep when needed.
- Show a fake notification just to keep the app alive. The notification must be honest.
- Use background location without `ACCESS_BACKGROUND_LOCATION`. Active sessions are foreground; background location is post-v1.0 (auto session detection).

## GPS signal loss

### The problem

GPS fails in tunnels, parking garages, dense urban canyons, and under heavy tree cover (dense forest is a real concern). Periods of 30 s to several minutes without a fix are normal.

### Detection

The `LocationCallback` simply stops being called when there's no fix. The app detects gaps two ways:

1. **Last-known-update timer.** A coroutine ticks every 5 seconds. If the time since the last GPS point exceeds 30 seconds, the session is marked "GPS gap in progress."
2. **Accuracy threshold.** Points with `accuracy_m > 50` are flagged but still stored. Sustained high-accuracy readings indicate a real signal problem.

### Handling

**Current behavior:**
- Gaps are stored as metadata: `metadata.gpsGaps = [{start, end, reason}]`.
- On the map, gaps are drawn as dashed lines connecting the last good point to the next good point.
- In the session list, gapped sessions get a small icon indicating "GPS gaps".
- Distance calculation skips gaps (does not interpolate, does not double-count).
- User can manually note in session notes ("crossed tunnel between 12 and 14 km").

**Future behavior (deferred):**
- Use a routing engine (GraphHopper, Valhalla) to snap the gap endpoints to the OSM road network and infer the most likely path.
- Mark interpolated points as `isSynthetic = true` so they don't pollute the real GPS data.

## Forgetting to start the session

### The problem

User drives for 20 minutes before remembering to tap START. Or starts mid-trip after pulling over.

### Mitigations

**Current behavior:** Honest about it. Session starts when START is tapped. The "missing" kilometres are simply not recorded. Acceptable because:
- The paper livret is the legal record anyway.
- Adding "estimate retroactively" features creates an opening for data invention that the app should not facilitate.

**v1.x:** Auto session detection prompts the user "Driving detected at 18:34 — start logging now?" so missed starts are rare.

**Educational:** Onboarding text mentions "Tap START before you leave the driveway. You can do it from the home-screen widget" (post-v1.0).

## Forgetting to stop the session

### The problem

User parks, walks away, forgets to tap STOP. Phone stays in pocket all night, or phone is plugged into car for hours after arrival.

### Mitigations

**Auto-stop after 90 minutes of speed < 3 km/h.** Default threshold; tunable in settings.

**Edge case: phone left in moving car without user.** If someone else drives the car after the session "should have" ended, the app records that driving as part of the session. The auto-pause detects stops, but doesn't detect "user is no longer present." This is acceptable — the user reviews the session before signing.

**Edge case: walking after parking.** Walking speed is ~5 km/h, just above the auto-pause threshold. To avoid logging post-drive walks as driving, the auto-stop's 90-minute window also requires that average speed over the last 5 minutes is below 8 km/h.

## The user has multiple sessions on one phone

### The problem

User starts a session, switches accounts, starts another? Not supported — single driver only.

But: user accidentally starts a second session while one is active. Possible race condition.

### Mitigations

The "Start" button is replaced with "Resume active session" while a session is ACTIVE. The DB enforces at most one ACTIVE session per driver via a partial unique index.

## Time zone changes

### The problem

User drives from France to Spain, crossing a time zone (sometimes). User changes time zone manually. Daylight Savings transition during a long drive.

### Mitigations

- All timestamps stored as `Instant` (UTC). Display formats to local time zone.
- Day/night calculation uses the actual sun position at the start location, not the local clock — this avoids DST quirks.
- Hash chain uses ISO-8601 in UTC, so the canonical payload is unambiguous.

## Phone shutdown mid-session

### The problem

Battery dies, user reboots, phone crashes. Session is left in ACTIVE state.

### Mitigations

On app launch, check for sessions in ACTIVE state:
- If `startedAt` is more than 6 hours ago: mark as DRAFT, set `endedAt = startedAt + 6h` (best-guess), warn user.
- Otherwise: prompt "A session was interrupted. Resume, save what was captured, or discard?"

The user makes the call.

## Storage full

### The problem

Phone runs out of storage during recording. Room writes fail. GPS points accumulate in memory.

### Mitigations

- GPS points are batched and written every 10 seconds, not on every callback. Smaller buffer.
- A storage check runs at session start: if free space < 50 MB, refuse to start with a clear message.
- During session: `IOException` on Room write triggers a "Storage problem" notification and pauses recording until resolved.

## Crash during signature

### The problem

Signature canvas crashes, app dies, signature is lost.

### Mitigations

- Signature PNG is written to disk before any hash is computed.
- If hashing fails after PNG write, the session stays in DRAFT — user re-signs.
- If hashing succeeds but Room update fails, the PNG is orphaned. A background cleanup job removes orphans on app start.

## Permission denied or revoked mid-session

### The problem

User revokes location permission while a session is active.

### Mitigations

- `LocationCallback` stops firing. Session is paused automatically.
- A foreground service notification updates: "Kilomètre — location permission revoked. Tap to fix."
- Tapping the notification opens app settings.
- If user re-grants, session resumes. If not, after 5 minutes paused without GPS, the session is force-stopped and saved as DRAFT.

## Database corruption

### The problem

SQLite DB file becomes corrupted. Rare but real.

### Mitigations

- Room uses SQLite's built-in journaling. Crashes mid-write don't typically corrupt the DB.
- If `RoomDatabase.openHelper` fails to open the DB: prompt user to restore from latest backup.
- Auto-backup nightly to app-private storage (last 7 days kept, rolling). User can disable.
- Manual backups via "Backup all data" are recommended in onboarding.

## SQLCipher key loss

### The problem

User enables encryption, forgets passphrase. Data is unrecoverable.

### Mitigations

- During enable, force user to confirm passphrase twice + acknowledge that loss = data loss + suggest writing it down.
- Optional: encryption uses a key derived from biometric + passphrase, so re-enrolling biometrics + remembering the passphrase recovers. (v0.x feature.)

## Backup restore on a different device

### The problem

User wipes phone, restores backup ZIP on a new phone.

### Mitigations

- Backup ZIP is self-contained. No device-specific data inside.
- App-private storage path is recreated on restore.
- Hash chain validates as long as the ZIP is intact.
- Caveat: if SQLCipher was enabled and the key was Keystore-derived (not passphrase), restore on a new device fails. Documented in Settings.

## Time skew

### The problem

User changes phone time backward. Sessions appear out of order in the chain.

### Mitigations

- Hash chain is ordered by `signedAt`, not by sequential ID. If the user backdates the clock, `signedAt` of a new session will be earlier than the previous one — the chain detects this and shows a warning ("Session signed earlier than previous; chain order suspect").
- No anti-cheat. Just visible truth.

## Conduite supervisée vs AAC

### The problem

The two schemes have different km targets, different minimum durations, and slightly different rules about who can be an accompagnateur.

### Mitigations

- Onboarding asks which scheme.
- Progress screen uses the scheme's km goal.
- The current version supports both at a basic level. Scheme-specific UI nuances come later.

## Multilingual accompagnateur name

### The problem

Accompagnateur's name contains accents, diacritics, or non-Latin characters.

### Mitigations

- All text is UTF-8 end-to-end (Room, JSON, file names).
- File names use a sanitized version (no accents, ASCII-only) for cross-platform compatibility, but the displayed name is the original.

## Driving outside France

### The problem

User drives in Spain, Belgium, or Italy.

### Mitigations

- GPS works globally. No code change needed.
- Open-Meteo works globally.
- Overpass works globally; road tags differ slightly per country but the classification is robust.
- Driving scheme remains AAC; nothing in the app cares about borders.
- Date formats remain locale-aware.
