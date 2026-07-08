# Features

> **Design document.** Describes the intended design and may run ahead of the current build (0.3.1). See `ROADMAP.md` for what has actually shipped.


Every feature Kilomètre has or intends to have, with acceptance criteria. This is a design catalogue of intent, NOT a ship-status list — see `ROADMAP.md` for what has actually shipped in the current release.


## Session lifecycle

### Start a session manually

The user taps a START button. The app:
- Verifies location permission is granted; prompts if not.
- Verifies notifications permission is granted; prompts if not.
- Creates an ACTIVE session row.
- Starts the foreground service.
- Navigates to the active-session screen.

**Acceptance:** From a cold app start, tapping START causes GPS points to begin recording within 3 seconds.

### Display live session stats

While a session is active, the app shows:
- Elapsed time.
- Distance so far (km).
- Current speed (km/h).
- A pause/resume button.
- A stop button.

Today: distance, current speed (read from the newest GPS sample, hidden while paused), and the pause/resume and stop controls are implemented. Elapsed time is specced here but not yet shown live.

**Acceptance:** Stats update at least once per second when the device has a GPS fix.

### Stop a session manually

The user taps STOP. The app:
- Updates the Session row to DRAFT with final stats.
- Stops the foreground service.
- Navigates to the signature screen.

Today: the on-screen STOP asks for confirmation first, to avoid ending a drive by mistake (a mistap would split one drive into two rows); the notification/lock-screen STOP stays one-tap. The signature screen is not built yet; STOP finalizes the row to DRAFT and returns to the Today screen.

**Acceptance:** STOP → signature screen transition completes in under 1 second.

### Auto-stop on prolonged inactivity

If speed remains below 3 km/h for 90 sustained minutes, the session automatically stops.

**Acceptance:** A test that simulates a parked phone for 95 minutes results in an auto-stopped session with `endedAt ≈ start + 90 min`.

### Auto-pause on short stops

If speed remains below 3 km/h for more than 5 minutes during an active session, the session enters a paused state. Paused time is excluded from the duration total. When speed exceeds 5 km/h again, the session resumes.

**Acceptance:** A 15-minute mid-drive stop appears in the session's `pausedSeconds` field as ~600 seconds (excluding the 5-minute threshold).

## Signature

Nothing in this section is built yet — the whole signature + hash-chain flow is the signing phase (see `docs/INTEGRITY.md`). All sessions are DRAFT until it lands. The `EditLog` entity and the `Session` hash columns exist in the schema but are unused.

### Draw signature on canvas

After STOP, the accompagnateur draws a signature on a fullscreen canvas. The canvas:
- Captures touch input with sub-pixel smoothing.
- Provides "Clear" and "Sign" buttons.
- Optionally displays the accompagnateur's name above the canvas.

**Acceptance:** Drawing a signature, tapping Sign, and returning to the sessions list completes in under 5 seconds.

### Bind signature to session via hash

When the user taps Sign:
- The canvas is rasterized to PNG.
- The PNG is saved to app-private storage.
- The `contentHash`, `signatureHash`, and `prevHash` are computed and stored.
- The session transitions to SIGNED state.

**Acceptance:** Editing the saved PNG file or any session field outside the app makes `IntegrityChecker.verify(sessionId)` return false.

### Drafts (unsigned sessions)

Sessions without a signature are DRAFT. They:
- Appear in the sessions list with a distinct marker.
- Do not count toward the km total.
- Can be signed retroactively, edited, or discarded.

**Acceptance:** A session in DRAFT state is excluded from the progress total. After signing, it appears in the total.

### Discard drafts older than 7 days prompt

Drafts older than 7 days prompt the user on app open: "This session is unsigned. Sign now, edit, or discard?"

## Map and route

### View route trace

In session detail, the map shows the GPS trace as a polyline. Initial camera frames the entire route.

**Acceptance:** A session with 1000 GPS points renders on the map in under 2 seconds.

### Timeline scrubber

A scrubber below the map lets the user move through the session's timeline. Position is highlighted on the map.

### Live map during active session

Toggleable in settings. Off by default for battery.

## Dashboard

### Today tab

- Big START button (or, while recording, PAUSE/RESUME + STOP).
- Idle hero with a compact "X km of Y km driven" progress line once any session exists.
- Live recording hero: pulsing dot, big distance number, current speed.

Built today. Note: no last-session card and no accompagnateur picker dropdown yet — a single accompagnateur is used automatically; a per-session picker for the 2+ case is planned.

### Sessions list

- Chronological list of all sessions (newest first).
- Each row shows: date, time range, duration, km, and a route-shape thumbnail.
- Tap → detail.

Built today. Not yet built: a DRAFT/SIGNED/DISCARDED state marker (all sessions are DRAFT until signing ships), the road-type icon, and pull-to-refresh integrity check.

### Progress tab

- Total km vs km goal (a circular ring with percentage), goal 3000 for AAC / 1000 for supervisée.
- AAC rendez-vous-pédagogique card when a milestone is reached (AAC mode).

Built today. Not yet built: total hours, session count, road-type breakdown chart, monthly km chart.

### Calendar heatmap

GitHub-style heatmap of driving days over the last year.

### Milestone notifications

Optional, toggleable ("Follow AAC milestones" mode). Built today. Notifies when crossing the AAC rendez-vous-pédagogique marks — 1000 km and 3000 km (the two km-relevant RDVs; the real AAC rules have no 2000 km RDV). Not a generic every-500 km notifier.

## Data enrichment

Nothing in this section is built yet — enrichment (day/night, weather, road-type) is a later phase. No network clients for Open-Meteo/Overpass exist yet.

### Auto day/night detection

For each session, compute sun position from start coordinates and timestamp. Mark `isNight = true` if the sun is below the horizon.

**Acceptance:** A session started at 23:00 in Paris in December is correctly marked as night.

### Auto weather lookup

After session end, query Open-Meteo with start coordinates + timestamp. Store weather code, temperature, precipitation in `metadata.weather`.

**Acceptance:** A session driven during light rain has `metadata.weather.precipitationMm > 0` after the post-session job runs.

### Auto road-type classification

After session end, query Overpass API with the GPS bounding box. Match each GPS point to the nearest road. Aggregate by `highway` tag. Store as percentages in `metadata.roadTypes`.

**Acceptance:** A session driven entirely on the A6 autoroute has `metadata.roadTypes.autoroute > 0.9`.

### Manual override of classifications

User can override the road-type breakdown or weather conditions in session detail.

## Settings

### Language toggle

French and English, both fully translated at launch.

### Driving scheme selection

As shipped: there is no scheme picker. The driver is AAC by default. What exists instead is a "Follow AAC milestones" toggle (AAC RDV ladder vs a single editable goal) in onboarding and Profile, plus an editable km goal in Profile. The `DrivingScheme` enum (AAC / SUPERVISEE / GENERIC) exists in the schema for later; supervisée/generic as first-class selectable modes are deferred.

### Accompagnateur management

Add, edit, delete accompagnateurs. Set a default for new sessions.

### Optional DB encryption

User can enable SQLCipher encryption. On enable, user sets a passphrase. The DB is migrated to encrypted form. Subsequent app launches require unlocking.

### Optional app lock

PIN or biometric lock on app launch. Independent from DB encryption.

### Battery optimization exemption prompt

On first session start, prompt the user to whitelist the app from battery optimization. Explain why. Link to the system setting. NOT yet built — on the reliability backlog; the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission is not yet declared.

### Crash log share

In Settings → About: "Share crash log" button. Triggers `ACTION_SEND` intent with `crash_log.txt` attached.

## Export and backup

### Export single session as GPX

In session detail, a share/export menu: Share (FileProvider), Save to device (Storage Access Framework), and Quick save into a configured default folder. Built today.

### Export all sessions as CSV

In Settings: "Export all as CSV". Saves to user-chosen folder. NOT yet built — only GPX export exists.

### Export full backup ZIP

In Settings: "Backup all data". Generates a ZIP containing GPX files, CSV, JSON, signatures, manifest. Saves to user-chosen folder. NOT yet built.

### Optional backup encryption

Toggle in backup dialog: "Encrypt backup". User provides a passphrase. Backup is AES-256-encrypted using PBKDF2-derived key.

### Restore from backup

In Settings: "Restore from backup". User selects a ZIP. App validates schema version and hash chain before importing. If chain is broken, user is warned and asked to confirm. NOT yet built.

## Integrity

Nothing in this section is built yet — integrity checking depends on the signing/hash-chain work (see `docs/INTEGRITY.md` and `docs/ROADMAP.md`).

### Verify hash chain on app open

Background task runs after app launch. Walks all SIGNED sessions in order. Checks every hash.

**Acceptance:** Modifying a row in the DB via `adb shell` causes a banner to appear on next app open.

### Show tampering in session detail

In session detail, if the hashes don't match, show a "Tampering detected" banner with details: which field changed, when.

## Onboarding

### First-launch onboarding

As shipped, a 6-step flow:
1. Language (English / Français, applied immediately).
2. Welcome + what the app does and doesn't do.
3. Driver name.
4. First accompagnateur (name, relation).
5. Follow AAC milestones? (AAC RDV ladder vs a single goal).
6. Permissions (fine + coarse location, notifications).

No scheme picker (see Driving scheme selection). Skipped on subsequent launches.

## F-Droid requirements

### Reproducible build

The release APK is built reproducibly. Same source → same APK.

### Fastlane metadata

`fastlane/metadata/android/{en-US,fr-FR}/` populated with title, description, screenshots, changelog.

### No tracking, no ads

Already locked in by principle. Documented in `docs/PRIVACY.md`.

### Anti-features disclosure

If `play-services-location` is accepted by F-Droid, the app may carry an `NonFreeDep` anti-feature flag. This is documented in the app description.

## Explicit non-features

These are NOT planned, by design:
- Account system, login, cloud sync.
- Real-time location sharing.
- Multi-driver support.
- Conduite supervisée-specific rules.
- OBD-II.

Parked for a future release.

## Explicit future features

- Auto session detection.
- Widget, quick-settings tile.
- PDF carnet matching the official livret.
- Live map during active session.
- Self-hosted backup target.
- GPS gap interpolation via routing engine.
- Tasker / Modes & Routines integration.
