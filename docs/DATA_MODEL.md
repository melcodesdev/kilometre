# Data Model

> **Design document.** Describes the intended design and may run ahead of the current build (0.3.0). See `ROADMAP.md` for what has actually shipped.


The Room database schema, the hash chain math, and the JSON metadata format used in the hybrid columns.

## Entities

### Driver

One row only (single-driver assumption for now). Stored as a singleton.

```kotlin
@Entity(tableName = "driver")
data class Driver(
    @PrimaryKey val id: Long = 1L,           // always 1 (single-driver)
    val name: String,
    val birthdate: LocalDate,
    val scheme: DrivingScheme,                // AAC, SUPERVISEE, GENERIC
    val startDate: LocalDate,                 // when AAC training began
    val kmGoal: Int,                          // 3000 for AAC, 1000 for supervisée
    val createdAt: Instant,
    val metadata: String                      // JSON blob, see below
)
```

### Accompagnateur

```kotlin
@Entity(tableName = "accompagnateur",
    foreignKeys = [ForeignKey(entity = Driver::class,
                              parentColumns = ["id"],
                              childColumns = ["driverId"])])
data class Accompagnateur(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverId: Long,
    val name: String,
    val relation: String,                     // "mother", "father", "other"
    val defaultSignaturePath: String?,        // optional pre-saved signature
    val createdAt: Instant,
    val metadata: String                      // JSON blob
)
```

### Session

The central entity.

```kotlin
@Entity(tableName = "session",
    foreignKeys = [
        ForeignKey(entity = Driver::class,
                   parentColumns = ["id"], childColumns = ["driverId"]),
        ForeignKey(entity = Accompagnateur::class,
                   parentColumns = ["id"], childColumns = ["accompagnateurId"])
    ],
    indices = [Index("driverId"), Index("accompagnateurId"), Index("startedAt"), Index("state")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverId: Long,
    val accompagnateurId: Long,

    val startedAt: Instant,
    val endedAt: Instant?,                    // null while session is active
    val pausedSeconds: Long = 0,              // accumulated pause time
    val manualPauseStartedAt: Instant? = null, // schema v2: non-null = live session is manually paused

    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,

    val startLat: Double?,
    val startLng: Double?,
    val endLat: Double?,
    val endLng: Double?,

    val state: SessionState,                  // ACTIVE, DRAFT, SIGNED, DISCARDED
    val notes: String = "",
    val manualEntry: Boolean = false,         // true if user typed it in rather than driving

    val signaturePath: String?,               // encrypted PNG in app-private storage (Keystore key)
    val signedAt: Instant?,

    val prevHash: String?,                    // SHA-256 hex of previous SIGNED session's contentHash
    val contentHash: String?,                 // SHA-256 of canonical session payload, set when signed
    val signatureHash: String?,               // SHA-256 of (contentHash || signature bytes || signedAt)

    val schemaVersion: Int = 1,
    val metadata: String = "{}"               // JSON blob for evolving fields
)
```

Implementation status: all columns above exist. `signaturePath`, `signedAt`, `prevHash`, `contentHash`, `signatureHash` are always null so far — they go live with signing. `notes` and `manualEntry` are written by nothing yet. Do not confuse the per-row `schemaVersion` field (the canonical-payload format version, still 1) with the Room DATABASE version, which is 2: the only schema change so far is the additive `manualPauseStartedAt` column, applied by a Room `AutoMigration(1, 2)`.

### GpsPoint

```kotlin
@Entity(tableName = "gps_point",
    foreignKeys = [ForeignKey(entity = Session::class,
                              parentColumns = ["id"], childColumns = ["sessionId"],
                              onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("timestamp")])
data class GpsPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Instant,
    val lat: Double,
    val lng: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val bearing: Float?,
    val isSynthetic: Boolean = false          // true for interpolated gap-fill points (a future version)
)
```

### EditLog

Append-only. Records every edit to a SIGNED session for audit purposes.

```kotlin
@Entity(tableName = "edit_log",
    foreignKeys = [ForeignKey(entity = Session::class,
                              parentColumns = ["id"], childColumns = ["sessionId"])])
data class EditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val fieldChanged: String,
    val oldValue: String,
    val newValue: String,
    val changedAt: Instant,
    val reason: String
)
```

## Enums

```kotlin
enum class DrivingScheme { AAC, SUPERVISEE, GENERIC }

enum class SessionState {
    ACTIVE,      // currently recording
    DRAFT,       // ended but not signed
    SIGNED,      // signed by accompagnateur; immutable except via EditLog
    DISCARDED    // marked as not counting (e.g., test session, accidental start)
}
```

## JSON metadata schema

The `metadata` column on Session is a JSON object. Fields evolve over time without requiring migrations. Reserved keys:

```json
{
  "weather": {
    "code": 61,
    "temperatureC": 14.5,
    "precipitationMm": 0.2,
    "fetchedAt": "2026-07-15T18:30:00Z",
    "source": "open-meteo"
  },
  "dayNight": {
    "isNight": false,
    "sunPositionDegrees": 35.2
  },
  "roadTypes": {
    "autoroute": 0.12,
    "primary": 0.34,
    "secondary": 0.21,
    "tertiary": 0.08,
    "residential": 0.25,
    "fetchedAt": "2026-07-15T18:32:00Z",
    "source": "overpass"
  },
  "gpsGaps": [
    {"start": "2026-07-15T18:15:00Z", "end": "2026-07-15T18:17:30Z", "reason": "low_accuracy"}
  ],
  "tags": ["autoroute_first_time", "night_driving"]
}
```

Unknown fields are preserved on read and written back unchanged. This is how the schema evolves without breaking older data.

## Hash chain math

Each SIGNED session has three hashes. They form a chain across all signed sessions for a driver.

### Canonical session payload

Before hashing, the session is serialized to a canonical string. The format is deterministic: fields in a fixed order, no whitespace, no JSON-formatting variation. This is critical — if the serialization is not deterministic, the hashes will differ between writes and the chain will appear broken.

```
v1|<id>|<driverId>|<accompagnateurId>|<startedAt_iso>|<endedAt_iso>|<pausedSeconds>|<distanceMeters_e6>|<durationSeconds>|<startLat_e7>|<startLng_e7>|<endLat_e7>|<endLng_e7>|<state>|<notes_b64>|<manualEntry>|<metadata_canonical>
```

Where `_e6` and `_e7` denote integer micro-units (multiplied by 10^6 or 10^7 and rounded) to avoid floating-point ambiguity, `_b64` is base64-encoded UTF-8, and `metadata_canonical` is the JSON object serialized with keys sorted alphabetically and no whitespace.

### contentHash

```
contentHash = SHA-256(prevHash || "\n" || canonicalPayload)
```

Where `prevHash` is the `contentHash` of the most recent SIGNED session for this driver. For the first signed session, `prevHash = "GENESIS"`.

### signatureHash

```
signatureHash = SHA-256(contentHash || "\n" || signatureFileBytes || "\n" || signedAt_iso)
```

The signature image (PNG bytes) is included so any edit to the signature pixels breaks the hash.

### GPS points are NOT in the chain

A separate Merkle-like rolling hash of GPS points is stored in the metadata blob as `gpsPointsHash`. Including every GPS point in the main hash would make the chain very large; computing it incrementally per session keeps the chain compact while still letting integrity checks detect tampering.

```
gpsPointsHash = SHA-256(p1 || p2 || ... || pn)
```

Where each `pi` is the canonical encoding of a GPS point: `<timestamp_ms>|<lat_e7>|<lng_e7>|<accuracy_mm>|<speed_mmps>`.

### Verifying the chain

On app open, a background task walks all SIGNED sessions in order. For each:

1. Recompute `contentHash` from the stored payload + previous session's `contentHash`. Compare with stored `contentHash`.
2. Recompute `signatureHash` from `contentHash` + signature file bytes + `signedAt`. Compare with stored `signatureHash`.
3. Recompute `gpsPointsHash` from the GpsPoint rows for this session. Compare with the stored value in metadata.

Any mismatch is logged and surfaced in the UI as a banner: "Session #N appears modified outside the app." The user is NOT blocked. The truth is just made visible.

## Storage layout

```
/data/data/dev.melcodes.kilometre/
├── databases/
│   └── kilometre.db                    — Room DB (optionally SQLCipher-encrypted)
├── files/
│   ├── signatures/
│   │   ├── session_001.png.enc         — signature, encrypted at rest (see below)
│   │   └── ...
│   └── crash_log.txt                   — local-only crash log
├── cache/
│   └── exports/                        — GPX written on demand, then shared out
└── datastore/
    └── kilometre_prefs.preferences_pb  — settings (theme, locale, AAC mode, …)
```

### Signature storage (encrypted at rest)

A handwritten signature is the most sensitive thing the app holds, so it is not
stored as a plain image. App-private storage keeps other apps out on a normal
device, but that alone is not enough: a debuggable build, an `adb backup`, or a
rooted/compromised phone could otherwise pull the raw PNG.

So each signature is encrypted with **AES-256-GCM using a key held in the Android
Keystore** (hardware-backed / StrongBox where available). The key never leaves the
device's secure element and is excluded from backups. The consequences:

- An `adb run-as` copy, an `adb backup`, or a rooted device yields **ciphertext**,
  not a usable signature.
- The plaintext exists only in memory, while the signature is being drawn,
  displayed, or hashed.
- `android:allowBackup` excludes the signatures directory, and the 1.0 release
  build is non-debuggable, closing the physical-access path.

When the user deliberately exports their own data (JSON export), the signature is
decrypted into that export — it is their data, going where they choose.

## Export format

### GPX (per session)

Standard GPX 1.1 with custom metadata extensions:

```xml
<gpx version="1.1" creator="Kilomètre 0.1">
  <metadata>
    <name>Session 42</name>
    <time>2026-07-15T18:00:00Z</time>
    <extensions>
      <kilometre:session id="42"
                         driverId="1"
                         accompagnateurId="1"
                         state="SIGNED"
                         distanceMeters="34521"
                         durationSeconds="2340"/>
      <kilometre:hashes contentHash="..."
                        signatureHash="..."
                        prevHash="..."
                        gpsPointsHash="..."/>
    </extensions>
  </metadata>
  <trk>
    <trkseg>
      <trkpt lat="48.405" lon="2.701">
        <ele>74.5</ele>
        <time>2026-07-15T18:00:01Z</time>
        <extensions>
          <kilometre:accuracy>4.2</kilometre:accuracy>
          <kilometre:speed>15.3</kilometre:speed>
        </extensions>
      </trkpt>
      <!-- ... -->
    </trkseg>
  </trk>
</gpx>
```

### CSV (one row per session)

```
id,startedAt,endedAt,driverName,accompagnateurName,distanceKm,durationMinutes,state,roadAutoroutePct,roadVillePct,isNight,weatherCode,signedAt,contentHash
42,2026-07-15T18:00:00Z,2026-07-15T18:39:00Z,Alex,Parent,34.521,39.0,SIGNED,0.12,0.55,false,61,2026-07-15T18:40:30Z,a3f9...
```

### JSON (everything)

The full export — sessions, GPS points, signatures (as base64), edit log, hashes — in a single JSON document. This is the canonical "give me all my data" format.

### Backup ZIP

```
kilometre-backup-2026-07-15.zip
├── manifest.json                    — app version, schema version, hash chain anchor
├── data.json                        — full JSON export
├── gpx/
│   ├── session_001.gpx
│   └── ...
├── signatures/
│   ├── session_001.png
│   └── ...
└── README.txt                       — explains the format for users
```

The whole ZIP is optionally AES-256-encrypted using a user-provided passphrase (PBKDF2-derived key, 600k iterations).

## Migrations

Room's auto-migrations handle additive changes (new columns with defaults, new tables, new indices). Manual migrations are needed for:

- Column type changes.
- Renaming columns or tables.
- Splitting or merging tables.
- Recomputing hashes when the canonical payload format changes (rare; requires a schema version bump and full re-hash).

Schema version is stored on each session row (`schemaVersion: Int = 1`). When the canonical payload format changes, all sessions are re-hashed using the new format, the old hashes are preserved in metadata for audit, and the change is documented in the project's history.
