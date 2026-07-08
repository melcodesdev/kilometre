# Architecture

The technical structure of Kilomètre: modules, layers, and how data flows.

## High-level shape

A single-Activity, Compose-only Android app with three logical layers:

```
┌─────────────────────────────────────────────┐
│  UI layer (Compose)                         │
│  - Composables                              │
│  - ViewModels                               │
│  - StateFlow / State                        │
├─────────────────────────────────────────────┤
│  Domain layer (pure Kotlin)                 │
│  - Session lifecycle logic                  │
│  - Hash chain computation                   │
│  - Road-type classification                 │
│  - Export formatters                        │
├─────────────────────────────────────────────┤
│  Data layer                                 │
│  - Room DAOs                                │
│  - Location service                         │
│  - Network clients (Open-Meteo, Overpass)   │
│  - File I/O (signatures, exports, backups)  │
└─────────────────────────────────────────────┘
```

The domain layer has no Android imports. It can be unit-tested on the JVM without an emulator.

## Module structure

For now, a single-module Gradle project keeps things simple. Multi-module split is deferred to v1.0 or later.

The tree below is the ACTUAL structure as of 0.3.x (session engine + map/viewing complete, plus the Settings screen and AAC-milestone tracking pulled forward). Sources live under `src/main/java/` (not `kotlin/`). Two deviations from the original plan are deliberate: there is no `di/` package (the manual DI container `AppContainer.kt` sits at the package root), and there are NO per-screen ViewModels — screens collect repository `Flow`s directly with `collectAsStateWithLifecycle`, because the state is thin and Room already owns it (see `docs/DECISIONS.md`). `KilometreNavHost` was never split out; the NavHost is defined inline in `MainActivity`.

```
kilometre/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/dev/melcodes/kilometre/
│       │   │   ├── MainActivity.kt              — single Activity, hosts the inline NavHost
│       │   │   ├── KilometreApp.kt              — Application class (DI, migrations, cold-boot recovery, crash logger)
│       │   │   ├── AppContainer.kt              — manual DI root (no di/ package)
│       │   │   ├── LocationService.kt           — foreground GPS service (root package, not data/location/)
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── KilometreDatabase.kt
│       │   │   │   │   ├── DriverDao.kt
│       │   │   │   │   ├── AccompagnateurDao.kt
│       │   │   │   │   ├── SessionDao.kt
│       │   │   │   │   ├── GpsPointDao.kt
│       │   │   │   │   ├── EditLogDao.kt        — exists for the signing phase; not written to yet
│       │   │   │   │   └── Converters.kt
│       │   │   │   └── network/
│       │   │   │       └── GithubUpdateClient.kt — reads the public GitHub Releases API (update check / what's new)
│       │   │   ├── domain/
│       │   │   │   ├── SessionRepository.kt
│       │   │   │   ├── SessionLifecycle.kt      — per-sample state machine: moving/pause/auto-stop
│       │   │   │   ├── AacMilestones.kt         — pure RDV-pédagogique ladder logic [1000, 3000]
│       │   │   │   ├── RouteSnapshot.kt         — Canvas route-thumbnail renderer (WebP, no tiles)
│       │   │   │   └── models/
│       │   │   │       ├── Session.kt           — Room entity
│       │   │   │       ├── GpsPoint.kt
│       │   │   │       ├── Driver.kt
│       │   │   │       ├── Accompagnateur.kt
│       │   │   │       ├── EditLog.kt
│       │   │   │       └── Enums.kt             — DrivingScheme, SessionState
│       │   │   └── ui/
│       │   │       ├── Tab.kt                   — the three bottom-nav destinations
│       │   │       ├── theme/
│       │   │       │   ├── Color.kt
│       │   │       │   ├── Theme.kt
│       │   │       │   └── Type.kt
│       │   │       ├── today/
│       │   │       │   └── TodayScreen.kt       — idle hero + live recording (distance, speed, pause/stop)
│       │   │       ├── sessions/
│       │   │       │   ├── SessionsScreen.kt    — list with route thumbnails
│       │   │       │   ├── SessionDetailScreen.kt — stats + route map + drive replay + GPX export
│       │   │       │   └── SessionFormat.kt     — shared locale-aware formatters
│       │   │       ├── progress/
│       │   │       │   └── ProgressScreen.kt    — goal ring + AAC RDV card
│       │   │       ├── settings/                — category hub + per-category screens
│       │   │       │   ├── SettingsScreen.kt        — the hub (category menu)
│       │   │       │   ├── SettingsComponents.kt    — shared internal row/group/picker widgets
│       │   │       │   ├── ProfileSettingsScreen.kt
│       │   │       │   ├── AppSettingsScreen.kt
│       │   │       │   ├── MapReplaySettingsScreen.kt
│       │   │       │   ├── AboutSettingsScreen.kt
│       │   │       │   └── AccompagnateurManagementScreen.kt
│       │   │       └── onboarding/
│       │   │           └── OnboardingScreen.kt  — 6 steps (language, welcome, name, accompagnateur, AAC mode, permissions)
│       │   └── res/
│       │       ├── values/strings.xml           — English
│       │       ├── values-fr/strings.xml        — French
│       │       └── ...
│       └── test/
│           └── java/dev/melcodes/kilometre/
│               └── domain/                       — JVM unit tests (SessionLifecycle, AacMilestones)
└── gradle/
    └── libs.versions.toml                       — version catalog
```

Planned homes for later phases (packages NOT yet created — listed so future code lands consistently): `data/network/` gains the Ktor Open-Meteo + Overpass clients (enrichment); `data/files/` gains the CSV/JSON exporters + backup ZIP (GPX export currently lives inline in `SessionDetailScreen`); `data/crypto/` gains the hash chain + canonical serializer + backup encryption; `domain/` gains `RoadClassifier`, `SunCalculator`, `IntegrityChecker`; `ui/signature/` gains the signature canvas + screen.

## Dependencies (Gradle version catalog)

`gradle/libs.versions.toml` is the single source of truth for dependency versions; the list here is a summary and can lag. Key versions actually in use as of 0.3.x:

```
kotlin = 2.2.10        agp = 9.2.1        ksp = 2.2.10-2.0.2
room = 2.7.0           coroutines = 1.9.0  kotlinx-datetime = 0.6.1
navigation-compose = 2.8.4   datastore-preferences = 1.1.1   appcompat = 1.7.0
maplibre-compose = 0.13.0    maplibre-compose-material3 = 0.13.0   (both BSD-3-Clause)
colorpicker-compose = 1.1.2 (Apache-2.0)   documentfile = 1.0.1 (Apache-2.0)
play-services-location = 21.3.0
```

Planned but NOT yet added: Ktor (network clients for enrichment — the current `GithubUpdateClient` uses `HttpsURLConnection` + `org.json`, no Ktor), kotlinx-serialization, sqlcipher-android (optional DB encryption). KSP (not KAPT) is the annotation processor for Room.

## Permissions (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

`INTERNET` + `ACCESS_NETWORK_STATE` were added for the user-initiated GitHub update check; every network call is explicit, the offline posture otherwise stands. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is planned (the battery-exemption prompt is still on the backlog) and NOT yet declared. `ACCESS_BACKGROUND_LOCATION` is deliberately NOT requested. Sessions are user-initiated; the app does not need location when in the background. This avoids the most invasive permission and the most aggressive battery optimization on Samsung devices.

## Data flow

Status as of 0.3.x: the "Starting a session" and "Viewing a past session" flows below are built (minus the timeline-scrubber and integrity-banner steps). The "Ending a session" flow is built only up to finalizing the row to DRAFT — everything from the signature screen onward (steps 5+) is not yet implemented. There are no ViewModels: composables collect repository `Flow`s directly with `collectAsStateWithLifecycle`; the numbered steps that mention a `…ViewModel` describe the original plan, not the current code.

### Starting a session

1. User taps "Start" on `TodayScreen`.
2. `TodayScreen` calls `SessionRepository.startSession(driverId, accompagnateurId)` (the accompagnateur is looked up via `AccompagnateurDao.firstForDriver(1L)`).
3. Repository inserts a Session row with `state = ACTIVE, startedAt = now()`.
4. `TodayScreen` calls `LocationService.start(ctx, sessionId, startedAt)`, which does `ContextCompat.startForegroundService`.
5. `LocationService.onStartCommand` calls `startForeground(id, notification)` immediately.
6. Service subscribes to `FusedLocationProviderClient` at a fixed 1 Hz HIGH_ACCURACY cadence (adaptive sampling was considered and dropped).
7. Each `LocationResult` callback writes a `GpsPoint` row and bumps `session.distanceMeters` incrementally via `SessionLifecycle`.
8. `TodayScreen` collects `SessionRepository.activeSession` and `currentSpeedMps`.
9. UI updates the live stats (km, current speed) as incoming points land.

### Ending a session

1. User taps "Stop" or auto-stop triggers (90 min sustained low speed).
2. Service computes final distance, duration, end coordinates.
3. Updates Session row: `state = DRAFT, endedAt = now()`.
4. Service stops itself with `stopSelf()`.
5. UI navigates to `SignatureScreen`.
6. Accompagnateur draws signature; canvas is rasterized to PNG.
7. `SignatureViewModel.sign(sessionId, signatureBytes)` calls repository.
8. Repository:
   - Stores PNG to app-private storage.
   - Computes `contentHash` (chains from previous SIGNED session).
   - Computes `signatureHash`.
   - Updates Session row: `state = SIGNED, signedAt = now()`, hash columns populated.
9. Background `WorkManager` job:
   - Calls Open-Meteo with start coordinates + timestamp; writes weather to metadata.
   - Calls Overpass with GPS bounding box; classifies road types; writes to metadata.
   - Recomputes hash chain (metadata changes invalidate the chain unless re-signed).
   - Note: weather and road classification fetched POST-signing, so they're in `metadata` but NOT in the hash chain. The hash chain protects the driving facts; enrichment is informational.

### Viewing a past session

1. User taps a session in `SessionsListScreen`.
2. Navigation pushes `SessionDetailScreen(sessionId)`.
3. `SessionDetailViewModel` loads the Session, GpsPoints, and signature.
4. Map composable renders the GPS trace.
5. Timeline scrubber shows time vs position; tapping the scrubber highlights the corresponding map point.
6. Integrity check runs in background; if hashes don't match, banner appears.

## Concurrency model

- Database reads use `Flow` and run on `Dispatchers.IO` automatically via Room.
- Database writes use `suspend` functions called from `viewModelScope` coroutines.
- Network calls run on `Dispatchers.IO` via Ktor.
- Hash computation runs on `Dispatchers.Default` (CPU-bound).
- All UI state collection happens in `Dispatchers.Main.immediate` via `collectAsStateWithLifecycle`.

## Error handling

- Domain logic returns `Result<T>` for fallible operations.
- UI exposes a `StateFlow<UiState>` with explicit `Loading`, `Success`, `Error` cases (sealed class).
- Crash log: any uncaught exception in the UI thread is caught by a `Thread.UncaughtExceptionHandler` installed in `Application.onCreate`. Writes to `files/crash_log.txt` with stack trace and minimal device info (Android version, app version). No PII, no network.
- The crash log file is shareable from Settings via Android's `ACTION_SEND` intent. The user chooses where to send it.
