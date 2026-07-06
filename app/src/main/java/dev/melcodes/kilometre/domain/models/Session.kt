package dev.melcodes.kilometre.domain.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

// One driving session — the central entity of the app. A session
// starts in ACTIVE, transitions to DRAFT when the driver taps STOP
// or auto-stop fires, then to SIGNED once the accompagnateur signs
// (Phase 3+). The transitions to SIGNED and DISCARDED are not yet
// possible in Phase 1.
//
// Nullable fields (endedAt, end coordinates, signature, hashes) are
// populated as the session progresses through its lifecycle. A row
// can have non-null endedAt and still be in state DRAFT — that's
// the normal post-Phase-1 state, awaiting a signature.
//
// `prevHash`, `contentHash`, `signatureHash` are the chained hashes
// that go live in Phase 3. Phase 1 writes the columns as null. See
// docs/DATA_MODEL.md for the hash chain math.
//
// `schemaVersion = 1` is the canonical-payload format version. If
// the format ever changes we re-hash all sessions and bump this.
// Phase 1 stays at 1.
//
// `metadata` is a JSON string for evolving fields (weather, road
// types, GPS gaps) added in later phases. Phase 1 writes "{}".
@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = Driver::class,
            parentColumns = ["id"],
            childColumns = ["driverId"],
        ),
        ForeignKey(
            entity = Accompagnateur::class,
            parentColumns = ["id"],
            childColumns = ["accompagnateurId"],
        ),
    ],
    indices = [
        Index("driverId"),
        Index("accompagnateurId"),
        Index("startedAt"),
        Index("state"),
    ],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverId: Long,
    val accompagnateurId: Long,

    val startedAt: Instant,
    val endedAt: Instant? = null,
    val pausedSeconds: Long = 0,

    // When the driver manually paused the live session, or null when the
    // session is recording (or finished). Non-null is the single source of
    // truth for "currently paused": the Today screen and the notification
    // both read it off the active row. On resume the elapsed span is folded
    // into pausedSeconds and this is cleared back to null. Schema v2.
    val manualPauseStartedAt: Instant? = null,

    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,

    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,

    val state: SessionState = SessionState.ACTIVE,
    val notes: String = "",
    val manualEntry: Boolean = false,

    val signaturePath: String? = null,
    val signedAt: Instant? = null,

    val prevHash: String? = null,
    val contentHash: String? = null,
    val signatureHash: String? = null,

    val schemaVersion: Int = 1,
    val metadata: String = "{}",
)
