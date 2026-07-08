package dev.melcodes.kilometre.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.melcodes.kilometre.domain.models.GpsPoint
import kotlinx.coroutines.flow.Flow

// DAO for GpsPoint rows. The foreground service writes one row per
// accepted location sample. Phase 1 reads back nothing — the live
// km counter comes from session.distanceMeters which the service
// maintains incrementally. Phase 2 reads the points back when the
// session-detail screen draws the route polyline on a map.
//
// Insert is a list insert too because the FusedLocationProviderClient
// callback can deliver several samples per onLocationResult call when
// the system catches up after a brief stall. Inserting them in one
// SQL statement is significantly faster than one row at a time.
@Dao
interface GpsPointDao {

    @Insert
    suspend fun insert(point: GpsPoint): Long

    @Insert
    suspend fun insertAll(points: List<GpsPoint>): List<Long>

    @Query("SELECT * FROM gps_point WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeForSession(sessionId: Long): Flow<List<GpsPoint>>

    // One-shot snapshot of a session's points in time order. Used by the
    // one-time distance recompute (SessionRepository.recomputeAllDistances)
    // to replay a trip through SessionLifecycle.
    @Query("SELECT * FROM gps_point WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun pointsForSession(sessionId: Long): List<GpsPoint>

    @Query("SELECT COUNT(*) FROM gps_point WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    // Returns the most recent point of a session, used by the service
    // to compute incremental distance without re-reading the whole
    // trip from disk every time a sample lands.
    @Query("SELECT * FROM gps_point WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestForSession(sessionId: Long): GpsPoint?

    // Reactive version of latestForSession: re-emits the newest point as
    // each sample lands. Backs the live current-speed readout on the
    // Today screen while a session records.
    @Query("SELECT * FROM gps_point WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestForSession(sessionId: Long): Flow<GpsPoint?>
}
