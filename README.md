# Kilomètre

> A privacy-respecting Android app for tracking conduite accompagnée driving sessions.

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Platform: Android 11+](https://img.shields.io/badge/Platform-Android%2011%2B-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Latest release](https://img.shields.io/github/v/release/melcodesdev/kilometre?label=release&color=success)](https://github.com/melcodesdev/kilometre/releases/latest)
[![Privacy: on-device](https://img.shields.io/badge/Privacy-on--device-success)](docs/PRIVACY.md)

**Status:** Active development (0.3.0 — the first public beta; still pre-1.0). Phase 1 (session engine) and Phase 2 (route map) are complete; signing and integrity (Phase 3) are next.

## What it is

Kilomètre is a logbook for the French AAC (apprentissage anticipé de la conduite) and conduite supervisée driving schemes. After completing instructor-led lessons, learners must drive a minimum number of kilometres with a parent or accompanying adult before taking the permit exam.

Since 2024, French learners are required to use Kopilote, a regulator-mandated digital livret that uploads driving data to the publisher's servers and shares it with the auto-école. The most visible alternative, Coach AAC, is published by an insurance group and requests always-on location for opaque automatic detection. Both produce data the user does not control and cannot meaningfully export. Kilomètre is a parallel personal record that does not replace either, but does what neither does: it stays on the phone.

The app:

- Records GPS routes locally during driving sessions.
- Captures the accompagnateur's signature on the device at the end of each session.
- Maintains a tamper-evident hash chain across sessions so the record is verifiably intact.
- Exports data as GPX, CSV, and JSON for use with any other tool.
- Works offline. Weather and road-type classification fetch in the background when a network appears.

## What it is not

- **Not a legal replacement for the paper *livret d'apprentissage*.** The official booklet remains the document the examiner sees. Kilomètre is a parallel record for accuracy, backup, and progress visibility.
- **Not anti-cheat software.** The integrity features make tampering visible to anyone reading the export. They do not prevent a determined user from editing their own data on their own device. That problem is not solvable on an open-source app and is not the intent.
- **Not a cloud service.** All data lives on the user's phone. The user owns their data; nothing leaves the device unless the user explicitly exports it.

## Status

Working Android app, tested on physical Android devices. GPS session recording, route map with gradient polyline, session list with thumbnails, progress tracker, AAC milestone reminders, and a full Settings screen with an in-app update check. Signed APKs are published via GitHub Releases and can be installed directly or through Obtainium.

See `docs/ARCHITECTURE.md` for the structure.

## Distribution

- Source: this repository (GitHub).
- Mirror: Codeberg, added at v1.0.
- Release channel for testers: GitHub APK releases.
- Stable release: F-Droid, after v1.0 ships.

## License

AGPL-3.0. See `LICENSE`. Anyone may read, modify, and redistribute this software. Modifications used to provide a service over a network must publish their source.

## Contributing

This is a solo project until v1.0. After release, contributions are welcome. Before then, please open an issue rather than a pull request - the architecture is still being shaped.

## Author

Built by [melcodesdev](https://github.com/melcodesdev). It began as a personal need for a driving-log tool that respects privacy, works offline, and produces data the user actually owns.
