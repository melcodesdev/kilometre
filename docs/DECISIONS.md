# Decisions

> **Design document.** Describes the intended design and may run ahead of the current build (0.3.0). See `ROADMAP.md` for what has actually shipped.


Every architectural and scope decision made during planning, with the alternatives considered and rejected. New decisions during development are recorded in the project's history; major ones get promoted here.

## Language and UI

### Kotlin over Java

Modern Android development is Kotlin-first. Java is supported but learning Java alongside Android adds friction with no benefit. Kotlin's null safety, coroutines, and Compose interop are all reasons enough on their own.

### Jetpack Compose over XML views

Compose is the modern UI toolkit. Learning XML views in 2026 would teach a deprecated pattern. Compose's state-driven model also fits the rest of the architecture (Flow → state → UI) cleanly.

**Rejected:** XML layouts. Maintained, but in maintenance mode. New apps should not use them.

### Material 3 over Material 2 or custom design

Material 3 is the default. The app ships with stock Material 3 components. Visual polish, dot-matrix aesthetics, custom theming — all deferred to post-v1.0.

## Architecture pattern

### MVVM with manual dependency injection

ViewModel → State → Composable is the standard Compose pattern. Manual DI (passing dependencies through constructors) is enough for a project this size.

**Rejected:** Hilt for now. The learning curve doesn't pay off until the project has many ViewModels and complex dependency graphs.

**Rejected:** MVI / Redux-style. Overkill for this scope. The simpler MVVM pattern with StateFlow is sufficient.

### Single-Activity, Compose-only navigation

One `MainActivity` hosting all composables via `Navigation Compose`. No Fragments.

## Persistence

### Room over raw SQLite

Type safety, compile-time SQL verification, migration support, Flow integration. Standard choice.

### Hybrid schema (typed core + JSON metadata column)

Core fields that are queried often (timestamps, km, session_id, accompagnateur_id) are typed columns. Evolving metadata (weather details, road classification breakdowns, user-added tags) lives in a JSON blob column. This balances type safety with iteration speed.

**Rejected:** Strict typed schema with migrations on every change — too slow to iterate during development.

**Rejected:** All-JSON document store — loses query performance and type safety for core fields.

### SQLCipher optional, off by default

Most users do not need at-rest encryption on a personal phone with screen lock. Those who do can enable it from settings. Adding SQLCipher unconditionally adds a ~2 MB native library and complicates backup/restore.

**Implementation:** `net.zetetic:sqlcipher-android` (4.13.0+ as of January 2026). The older `android-database-sqlcipher` package is deprecated. The encryption key is derived from a user-supplied passphrase via PBKDF2 (600k iterations) and stored in the Android Keystore.

## Maps

### MapLibre Compose over osmdroid

MapLibre Native is the actively-maintained open-source vector map renderer (originally forked from Mapbox before Mapbox closed-sourced). MapLibre Compose wraps it for Jetpack Compose. As of 2025, several projects have migrated from osmdroid to MapLibre specifically because osmdroid development has stalled and it doesn't integrate cleanly with Compose.

**Rejected:** osmdroid. Old, poorly documented, development effectively stopped.

**Rejected:** Google Maps SDK. Proprietary, requires API key, requires Play Services beyond what F-Droid accepts.

**Rejected:** Mapbox SDK. Closed-source since 2020.

### MapLibre vector tiles (OpenFreeMap)

The route map uses MapLibre Compose with OpenFreeMap's keyless vector tiles. Vector tiles render crisply at any zoom and OpenFreeMap needs no API key or self-hosted tile server. (An earlier plan to start with raster tiles was dropped in favour of going straight to vector.)

## Location

### FusedLocationProviderClient over LocationManager

`FusedLocationProviderClient` is more battery-efficient, fuses GPS/Wi-Fi/cell for better indoor accuracy, and is the recommended Android API. It does require `play-services-location`, which is a Google Play Services dependency.

**F-Droid concern:** F-Droid generally rejects Play Services. As of 2026, the location-only Play Services dependency is sometimes accepted with an anti-feature flag. If F-Droid rejects this dependency, the fallback is `LocationManager` from the AOSP framework. The fallback is less efficient but always works.

The Phase 0 spike uses `FusedLocationProviderClient`. If F-Droid acceptance becomes blocking, a refactor to `LocationManager` is straightforward — same interface shape, different battery profile.

### Adaptive sampling

The GPS sampling rate adjusts to the current speed:
- Speed > 30 km/h: 2 s interval, `PRIORITY_HIGH_ACCURACY`.
- 5–30 km/h: 5 s interval, `PRIORITY_HIGH_ACCURACY`.
- < 5 km/h: 15 s interval, `PRIORITY_BALANCED_POWER_ACCURACY`.
- Accuracy degraded (> 50 m reported): force 1 s interval until signal recovers.

**Rejected:** Fixed 1 Hz sampling. Burns battery during stops with no benefit.

**Rejected:** User-configurable sampling. Adds settings complexity for a decision the app can make automatically.

## Background work

### Foreground service with `foregroundServiceType="location"`

Required since Android 14. Shows a persistent notification, which is honest to the user about what the app is doing. Samsung's One UI 6.0+ commits to respecting foreground services that follow this policy correctly.

**Rejected:** WorkManager. Designed for deferrable background work, not continuous active tracking.

**Rejected:** Plain Service without foreground status. Will be killed by Doze and battery optimization.

## Network

### Ktor client over Retrofit

Ktor is Kotlin-first, multiplatform, FOSS, and integrates cleanly with coroutines. Retrofit is widely used but Java-rooted and overkill for the two APIs Kilomètre calls.

### APIs

- **Open-Meteo** for weather. Free, no API key, FOSS-aligned. Called once at session end with start coordinates + timestamp.
- **Overpass API** for OSM road tags. Free, no key. Called post-session with the GPS bounding box.

**Rejected:** OpenWeatherMap, AccuWeather — require API keys.

**Rejected:** Google Roads API — proprietary, requires key, requires Play Services.

### Network failure handling

All network calls are deferred. They run in a `WorkManager` job after a session ends. If the device is offline, the job is queued and runs when connectivity returns. The user never sees a network error during driving.

## Identity and integrity

### SHA-256 hash chain across sessions

Each session's content (timestamps, km, GPS points, accompagnateur info, signature image) is hashed. The hash includes the previous session's hash, forming a chain. Tampering with any historical session breaks the chain visibly.

**Rejected:** BLAKE3. Faster than SHA-256, but requires a JNI dependency for the Kotlin port. SHA-256 is in `java.security` and is fast enough at this scale.

**Rejected:** Cryptographic signatures with the device's hardware key. Would tie data to a specific device and complicate backup/restore across devices.

### Signature canvas built in Compose

Roughly 150 lines of Compose `Canvas` and pointer-input code. Avoids adding a signature library dependency. The signature is rasterized to PNG, stored alongside the session, and included in the hash chain.

**Rejected:** Drawing libraries from Maven Central. Most are abandoned or have heavyweight dependencies for what is a simple drawing surface.

## Distribution

### F-Droid + GitHub APK releases

F-Droid is the FOSS distribution channel that matters. GitHub APK releases serve as beta/preview channel for testers before each F-Droid update. Codeberg mirror is added at v1.0 for those who prefer it to GitHub.

**Rejected:** Google Play Store. Requires a developer account (paid), requires Google's developer verification (incompatible with the project's values), and competes with the F-Droid first-party experience.

**Rejected:** IzzyOnDroid as a stepping stone. Useful for some projects, but Kilomètre's anti-features should be zero, so the main F-Droid repo is the goal.

### AGPL-3.0

See the project's decision history.
