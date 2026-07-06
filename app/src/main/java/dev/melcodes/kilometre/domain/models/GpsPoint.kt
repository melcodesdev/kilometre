package dev.melcodes.kilometre.domain.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

// One GPS sample emitted by the foreground service. Cascade-deletes
// with the parent session: removing a session removes its samples.
// At ~1 Hz a 60-minute drive produces ~3600 rows; that's well within
// SQLite's comfort zone but is why we index by sessionId + timestamp
// for fast detail-screen queries.
//
// `isSynthetic` is reserved for the v1.1+ gap-fill feature where we
// interpolate across missing GPS windows. Phase 1 always writes false.
@Entity(
    tableName = "gps_point",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("timestamp")],
)
data class GpsPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Instant,
    val lat: Double,
    val lng: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float,
    val speedMps: Float? = null,
    val bearing: Float? = null,
    val isSynthetic: Boolean = false,
)
