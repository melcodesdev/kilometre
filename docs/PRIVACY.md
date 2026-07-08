# Privacy

What data Kilomètre touches, where it goes, and what we promise. This document
describes the app as it is **today (0.3.1)** and flags what is planned but not yet
built. If this ever diverges from `README.md`, this document wins.

## Summary

Kilomètre stores driving data on your phone and does not upload it anywhere. As
of 0.3.1 the app is effectively offline: the only outbound network call is an
**optional, manual update check** you trigger yourself in Settings. There is no
account, no telemetry, and no cloud. You can export your data in open formats,
delete individual sessions in the app, and delete everything by uninstalling.

## What it stores (on the device only)

- **Driver information** entered during onboarding: name, driving goal.
- **Accompagnateur information**: name, relation.
- **Sessions**: start/end timestamps, GPS coordinates throughout the drive,
  distance, duration, and pause information.
- **App settings**: language, theme, map/replay preferences, AAC-mode tracking
  state, and the chosen export folder.

All of it lives in app-private storage (`/data/data/dev.melcodes.kilometre/`),
which other apps cannot read on a non-rooted device.

**Planned (not yet in the app):** the accompagnateur's signature image (Phase 3),
a tamper-evident hash chain and edit log over signed sessions (Phase 3), and
weather / road-type enrichment (Phase 4). This document will be updated when they
land.

### What it never collects

Phone number, email, contacts, photos (other than a signature you draw, once that
feature exists), browser/app-usage history, biometric data, advertising
identifiers, or a user account — because there is no account system.

## Network calls

As of 0.3.1 the app makes exactly one kind of outbound call:

### Update check (manual)

In Settings → About, "Check for updates" and "What's new" call the public GitHub
Releases API for this project. This only happens when you tap those rows — nothing
polls in the background. If installed via F-Droid or Obtainium, that client
handles updates instead and the in-app check is redundant.

### Planned network calls (Phase 4, not yet built)

When enrichment ships, the app will, **after a session ends**, optionally call
Open-Meteo (weather) and the Overpass API (road types) — both keyless,
registration-free, and individually disable-able. Until then, the app makes no
such calls. This section will describe them in full when they exist.

### Crash reports

No automatic crash reporting. On a crash the app writes a local log file; you can
choose to share it from Settings → About → "Share crash log" via the Android share
sheet. It is never sent automatically.

### What it never calls

Google Analytics, Firebase, Sentry/Bugsnag/Crashlytics or any crash service, any
advertising network, any social SDK, any auto-école management system, any
insurance API. None are present in the app.

## Permissions

The permissions Kilomètre actually declares (0.3.1), and why:

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Record GPS during driving sessions. |
| `ACCESS_COARSE_LOCATION` | Fallback if fine location is denied. |
| `FOREGROUND_SERVICE` | Run the session-recording service. |
| `FOREGROUND_SERVICE_LOCATION` | A location-using foreground service (Android 14+). |
| `POST_NOTIFICATIONS` | Show the session-active notification (Android 13+). |
| `INTERNET` | The manual update check (and, later, the Phase-4 enrichment calls). |
| `ACCESS_NETWORK_STATE` | Let the update check fail fast when offline. |

Deliberately **not** requested: `ACCESS_BACKGROUND_LOCATION` (sessions are
user-initiated and recorded in a visible foreground service), contacts
permissions, and legacy external-storage permissions (exports use the Storage
Access Framework).

## Data sharing

The app shares nothing — it runs entirely on your device. If you export data with
the in-app GPX export, that file is yours to send wherever you like. What happens
to it after export is your decision.

## Data deletion

- **One session:** open it and choose Delete. This removes the session and its
  GPS points permanently.
- **Everything:** uninstall the app. Android wipes all app-private storage on
  uninstall. (A dedicated in-app "clear all data" screen is planned.)

## Security

- All data sits in app-private storage (unreadable by other apps on a non-rooted
  device).
- Release builds are debuggable during development so the database can be
  inspected over `adb` for backup/recovery; this will be turned off for the 1.0
  public release.

**Planned (Phase 3):** the accompagnateur's signature is stored encrypted at
rest with a hardware-backed Android Keystore key, so an `adb` pull, a backup, or a
rooted device yields ciphertext rather than a usable signature image.

**Planned (Phase 6):** optional database encryption (SQLCipher), an app lock, and
encrypted backup export. None of these exist yet — do not rely on them.

**What no app can protect against:** someone with your unlocked phone, root
access, or a compromised OS. Use a device screen lock.

## Children's privacy

The AAC scheme is open to learners aged 15 and up. The app collects no
age-restricted data, has no social or communication features, and does not market
to children. Use by minors with parental knowledge is expected and supported.

## Promises that will never change

No cloud sync. No telemetry, ever. No advertising. No account system. No
partnerships with insurance companies. If those ever change, it is no longer
Kilomètre — use a fork.

## Contact

Privacy questions: open an issue on the GitHub repo.

## Legal

Provided in good faith, not a legal contract. The AGPL-3.0 license disclaims
warranty. This describes intended behavior; bugs are possible — report them.

## Last updated

For 0.3.1.
