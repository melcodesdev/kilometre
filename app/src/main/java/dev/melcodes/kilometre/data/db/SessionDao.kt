package dev.melcodes.kilometre.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.melcodes.kilometre.domain.models.Session
import dev.melcodes.kilometre.domain.models.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

// DAO for Session rows. Owns the queries the repository and the
// foreground service need: insert a fresh ACTIVE row, update its
// progress and final state, observe the active session, and list
// past sessions for the Sessions tab.
//
// `observeActiveSession()` returns a Flow that emits the single
// ACTIVE row (or null). Phase 1 enforces at-most-one ACTIVE row
// by checking before insert; if recovery from a crash ever finds
// two, the older one is force-finalised to DRAFT by the Application.
@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    // Incremental distance update. Called once per accepted GPS sample
    // by the foreground service. Doing the addition in SQL avoids a
    // read-modify-write race when samples land back-to-back.
    @Query("UPDATE session SET distanceMeters = distanceMeters + :delta WHERE id = :id")
    suspend fun addDistance(id: Long, delta: Double)

    // Increment paused seconds atomically when a pause window closes.
    @Query("UPDATE session SET pausedSeconds = pausedSeconds + :delta WHERE id = :id")
    suspend fun addPausedSeconds(id: Long, delta: Long)

    // Set (pause) or clear (resume) the manual-pause marker. Null means the
    // session is recording again. Used by the manual Pause/Resume controls.
    @Query("UPDATE session SET manualPauseStartedAt = :at WHERE id = :id")
    suspend fun setManualPauseStartedAt(id: Long, at: Instant?)

    @Query("UPDATE session SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: SessionState)

    // Delete a single session row. Its GPS points cascade-delete with it
    // (gps_point has onDelete = CASCADE). Used when a recording is stopped
    // with zero distance — an empty session not worth keeping.
    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteById(id: Long)

    // One-time cleanup of empty sessions left over before zero-distance
    // recordings were discarded on stop. Excludes the live ACTIVE row so
    // a just-started session (distance still 0.0) is never deleted out
    // from under the foreground service.
    @Query("DELETE FROM session WHERE distanceMeters = 0.0 AND state <> 'ACTIVE'")
    suspend fun deleteZeroDistanceSessions()

    @Query("""
        UPDATE session
        SET state = :state,
            endedAt = :endedAt,
            durationSeconds = :durationSeconds,
            endLat = :endLat,
            endLng = :endLng
        WHERE id = :id
    """)
    suspend fun finalize(
        id: Long,
        state: SessionState,
        endedAt: Instant,
        durationSeconds: Long,
        endLat: Double?,
        endLng: Double?,
    )

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT * FROM session WHERE id = :id")
    fun observeById(id: Long): Flow<Session?>

    @Query("SELECT * FROM session WHERE state = 'ACTIVE' LIMIT 1")
    fun observeActiveSession(): Flow<Session?>

    @Query("SELECT * FROM session WHERE state = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): Session?

    @Query("SELECT * FROM session ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Session>>

    // One-shot snapshot of all sessions. Used by the one-time distance
    // recompute (SessionRepository.recomputeAllDistances).
    @Query("SELECT * FROM session")
    suspend fun getAll(): List<Session>

    // Overwrite a session's cached distance. Distinct from addDistance
    // (incremental, used live by the service); this sets an absolute
    // value, used by the recompute to replace a wrong total.
    @Query("UPDATE session SET distanceMeters = :meters WHERE id = :id")
    suspend fun setDistance(id: Long, meters: Double)

    // Sum of distances for completed (non-DISCARDED) sessions belonging
    // to a driver. Used by the Progress tab. COALESCE keeps the return
    // type non-null when no sessions exist yet.
    @Query("""
        SELECT COALESCE(SUM(distanceMeters), 0.0)
        FROM session
        WHERE driverId = :driverId AND state <> 'DISCARDED'
    """)
    fun observeTotalDistanceMeters(driverId: Long): Flow<Double>
}
