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

```
kilometre/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/dev/melcodes/kilometre/
│       │   │   ├── MainActivity.kt
│       │   │   ├── KilometreApp.kt              — Application class
│       │   │   ├── di/                          — manual DI
│       │   │   │   └── AppContainer.kt
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── KilometreDatabase.kt
│       │   │   │   │   ├── DriverDao.kt
│       │   │   │   │   ├── SessionDao.kt
│       │   │   │   │   ├── GpsPointDao.kt
│       │   │   │   │   ├── EditLogDao.kt
│       │   │   │   │   └── Converters.kt
│       │   │   │   ├── location/
│       │   │   │   │   ├── LocationService.kt   — foreground service
│       │   │   │   │   ├── LocationSampler.kt   — adaptive sampling logic
│       │   │   │   │   └── LocationNotification.kt
│       │   │   │   ├── network/
│       │   │   │   │   ├── KtorClient.kt
│       │   │   │   │   ├── OpenMeteoApi.kt
│       │   │   │   │   └── OverpassApi.kt
│       │   │   │   ├── files/
│       │   │   │   │   ├── SignatureStore.kt
│       │   │   │   │   ├── GpxExporter.kt
│       │   │   │   │   ├── CsvExporter.kt
│       │   │   │   │   ├── JsonExporter.kt
│       │   │   │   │   └── BackupZipper.kt
│       │   │   │   └── crypto/
│       │   │   │       ├── HashChain.kt
│       │   │   │       ├── CanonicalSerializer.kt
│       │   │   │       └── BackupEncryption.kt
│       │   │   ├── domain/
│       │   │   │   ├── SessionRepository.kt
│       │   │   │   ├── SessionLifecycle.kt      — start, pause, resume, stop, auto-stop
│       │   │   │   ├── RoadClassifier.kt
│       │   │   │   ├── SunCalculator.kt
│       │   │   │   ├── IntegrityChecker.kt
│       │   │   │   └── models/
│       │   │   │       ├── Session.kt           — Room entity
│       │   │   │       ├── GpsPoint.kt
│       │   │   │       ├── Driver.kt
│       │   │   │       ├── Accompagnateur.kt
│       │   │   │       └── EditLog.kt
│       │   │   └── ui/
│       │   │       ├── theme/
│       │   │       │   ├── Color.kt
│       │   │       │   ├── Theme.kt
│       │   │       │   └── Type.kt
│       │   │       ├── nav/
│       │   │       │   └── KilometreNavHost.kt
│       │   │       ├── today/
│       │   │       │   ├── TodayScreen.kt
│       │   │       │   └── TodayViewModel.kt
│       │   │       ├── sessions/
│       │   │       │   ├── SessionsListScreen.kt
│       │   │       │   ├── SessionsListViewModel.kt
│       │   │       │   ├── SessionDetailScreen.kt
│       │   │       │   └── SessionDetailViewModel.kt
│       │   │       ├── progress/
│       │   │       │   ├── ProgressScreen.kt
│       │   │       │   └── ProgressViewModel.kt
│       │   │       ├── active/
│       │   │       │   ├── ActiveSessionScreen.kt
│       │   │       │   └── ActiveSessionViewModel.kt
│       │   │       ├── signature/
│       │   │       │   ├── SignatureScreen.kt
│       │   │       │   ├── SignatureCanvas.kt
│       │   │       │   └── SignatureViewModel.kt
│       │   │       ├── settings/                  — category hub + per-category screens
│       │   │       │   ├── SettingsScreen.kt        — the hub (category menu)
│       │   │       │   ├── SettingsComponents.kt    — shared internal row/group/picker widgets
│       │   │       │   ├── ProfileSettingsScreen.kt
│       │   │       │   ├── AppSettingsScreen.kt
│       │   │       │   ├── MapReplaySettingsScreen.kt
│       │   │       │   ├── AboutSettingsScreen.kt
│       │   │       │   └── AccompagnateurManagementScreen.kt
│       │   │       ├── onboarding/
│       │   │       │   ├── OnboardingScreen.kt
│       │   │       │   └── OnboardingViewModel.kt
│       │   │       └── common/
│       │   │           ├── PermissionRequest.kt
│       │   │           ├── ErrorBanner.kt
│       │   │           └── EmptyState.kt
│       │   └── res/
│       │       ├── values/strings.xml           — English
│       │       ├── values-fr/strings.xml        — French
│       │       └── ...
│       └── test/
│           └── kotlin/dev/melcodes/kilometre/
│               └── domain/                       — JVM unit tests
└── gradle/
    └── libs.versions.toml                       — version catalog
```

## Dependencies (Gradle version catalog)

```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.2"
compose-bom = "2026.05.00"
room = "2.7.0"
ksp = "2.0.21-1.0.27"
coroutines = "1.9.0"
ktor = "3.0.1"
maplibre-compose = "0.10.1"
play-services-location = "21.3.0"
sqlcipher = "4.13.0"

[libraries]
androidx-core = { module = "androidx.core:core-ktx", version = "1.13.1" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version = "2.8.7" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version = "2.8.7" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version = "1.9.3" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version = "2.8.4" }

compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }

room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.7.3" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.1" }

ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-android = { module = "io.ktor:ktor-client-android", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

maplibre-compose = { module = "org.maplibre.compose:maplibre-compose", version.ref = "maplibre-compose" }

play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "play-services-location" }

sqlcipher = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher" }
androidx-sqlite = { module = "androidx.sqlite:sqlite", version = "2.4.0" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Versions above are current as of May 2026 and will need updating during development. The version catalog is the single source of truth for dependency versions.

## Permissions (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

`ACCESS_BACKGROUND_LOCATION` is deliberately NOT requested. Sessions are user-initiated; the app does not need location when in the background. This avoids the most invasive permission and the most aggressive battery optimization on Samsung devices.

## Data flow

### Starting a session

1. User taps "Start" on `TodayScreen`.
2. `TodayViewModel` calls `SessionRepository.startSession(driverId, accompagnateurId)`.
3. Repository inserts a Session row with `state = ACTIVE, startedAt = now()`.
4. Repository calls `ContextCompat.startForegroundService(intent)` to start `LocationService`.
5. `LocationService.onStartCommand` calls `startForeground(id, notification)` immediately.
6. Service subscribes to `FusedLocationProviderClient` with adaptive sampling parameters.
7. Each `LocationResult` callback writes a `GpsPoint` row tagged with the session ID.
8. `ActiveSessionViewModel` collects `sessionDao.observeActiveSession()` as a `StateFlow`.
9. UI updates the live stats (km, duration) based on incoming points.

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
