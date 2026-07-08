package dev.melcodes.kilometre.domain

import dev.melcodes.kilometre.data.db.GpsPointDao
import dev.melcodes.kilometre.data.db.SessionDao
import dev.melcodes.kilometre.domain.models.GpsPoint
import dev.melcodes.kilometre.domain.models.Session
import dev.melcodes.kilometre.domain.models.SessionState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// The repository the UI and the service both talk to for session
// state. Owns no in-memory session model — everything is in Room and
// observed via Flows from the DAOs. This is the inversion that lets
// the foreground service stop exposing a static StateFlow: the UI
// reads from Room, the service writes to Room, neither knows about
// the other.
//
// `clock` is injected so unit tests can advance time deterministically;
// production wires it to `Clock.System`.
class SessionRepository(
    private val sessionDao: SessionDao,
    private val gpsPointDao: GpsPointDao,
    private val cacheDir: File,
    private val clock: Clock = Clock.System,
) {
    // Currently-active session, or null if none. The Flow re-emits
    // whenever the underlying row changes, including when distance
    // and pausedSeconds increment.
    val activeSession: Flow<Session?> = sessionDao.observeActiveSession()

    val allSessions: Flow<List<Session>> = sessionDao.observeAll()

    fun totalDistanceMeters(driverId: Long): Flow<Double> =
        sessionDao.observeTotalDistanceMeters(driverId)

    // Live current speed (m/s) of the active session's most recent GPS
    // sample, or null when nothing is recording or the newest sample
    // carried no speed. flatMapLatest re-subscribes to the newest active
    // session's latest-point flow whenever the active row changes, so the
    // Today screen collects a single flow regardless of which session is
    // live. The service pauses GPS on manual pause, so no new samples land
    // and the value simply stays put until resume — the screen hides it
    // while paused rather than showing a stale number.
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSpeedMps: Flow<Float?> = activeSession.flatMapLatest { session ->
        if (session == null) flowOf(null)
        else gpsPointDao.observeLatestForSession(session.id).map { it?.speedMps }
    }

    // One session observed by id, for the detail screen. Re-emits when the
    // row changes (a future edit, a signature) and emits null if the row
    // doesn't exist — e.g. it was deleted while the detail screen was open.
    fun session(id: Long): Flow<Session?> = sessionDao.observeById(id)

    // Every GPS point of one session, oldest first, observed reactively.
    // The detail screen turns these into the route polyline.
    fun points(sessionId: Long): Flow<List<GpsPoint>> =
        gpsPointDao.observeForSession(sessionId)

    // Insert a fresh ACTIVE session row. Caller (typically the
    // foreground service) is responsible for actually starting GPS
    // collection — the repository only owns the DB side.
    //
    // If an ACTIVE session already exists (e.g. crash recovery before
    // the Application class finalized it), return that row instead of
    // creating a second one. Phase 1 enforces at-most-one ACTIVE row
    // this way.
    suspend fun startSession(driverId: Long, accompagnateurId: Long): Session {
        sessionDao.getActiveSession()?.let { existing -> return existing }
        val now = clock.now()
        val draft = Session(
            driverId = driverId,
            accompagnateurId = accompagnateurId,
            startedAt = now,
            state = SessionState.ACTIVE,
        )
        val id = sessionDao.insert(draft)
        return draft.copy(id = id)
    }

    // Apply one accepted GPS sample to the database: insert the point,
    // optionally bump distance, optionally close a pause window.
    // `autoStop` is reported by the lifecycle classifier when a pause
    // has held for 90 minutes; the caller decides whether to act on it
    // (typically by calling stopSession()).
    suspend fun applySample(
        sessionId: Long,
        point: GpsPoint,
        decision: Decision.Accept,
    ) {
        gpsPointDao.insert(point)
        if (decision.distanceDelta > 0.0) {
            sessionDao.addDistance(sessionId, decision.distanceDelta)
        }
        val pauseEvent = decision.pauseEvent
        if (pauseEvent is PauseEvent.Exited) {
            // Accumulate the closed pause window into the row. Round
            // to whole seconds to match the column's resolution.
            val seconds = pauseEvent.durationMs / 1000L
            if (seconds > 0L) {
                sessionDao.addPausedSeconds(sessionId, seconds)
            }
        }
    }

    // Mark the session DRAFT, set endedAt and durationSeconds, persist
    // final end coordinates. Idempotent: calling stop on an already-
    // finalized session is a no-op.
    //
    // The caller (service or UI) is responsible for telling the
    // foreground service to stop after this returns.
    suspend fun stopSession(
        sessionId: Long,
        endLat: Double? = null,
        endLng: Double? = null,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.state != SessionState.ACTIVE) return
        // An empty drive (no accepted movement) is not worth keeping: it
        // clutters the Sessions list and counts for nothing on Progress.
        // Delete it instead of finalising. GPS points cascade out with it.
        if (session.distanceMeters == 0.0) {
            sessionDao.deleteById(sessionId)
            return
        }
        val endedAt = clock.now()
        val durationSeconds =
            (endedAt.toEpochMilliseconds() - session.startedAt.toEpochMilliseconds()) / 1000L -
                session.pausedSeconds
        sessionDao.finalize(
            id = sessionId,
            state = SessionState.DRAFT,
            endedAt = endedAt,
            durationSeconds = durationSeconds.coerceAtLeast(0L),
            endLat = endLat,
            endLng = endLng,
        )
        generateRouteSnapshot(sessionId)
    }

    // Manually pause the live session: stamp manualPauseStartedAt = now so
    // the UI and notification flip to a paused state. The caller (the
    // foreground service) stops GPS updates separately; this only owns the DB
    // marker. No-op if the session isn't ACTIVE or is already paused.
    suspend fun pauseSession(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.state != SessionState.ACTIVE) return
        if (session.manualPauseStartedAt != null) return
        sessionDao.setManualPauseStartedAt(sessionId, clock.now())
    }

    // Resume a manually-paused session: fold the elapsed pause span into
    // pausedSeconds (so it's excluded from the final duration, like an auto-
    // pause) and clear the marker. The caller restarts GPS separately. No-op
    // if the session isn't paused.
    suspend fun resumeSession(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        val pausedAt = session.manualPauseStartedAt ?: return
        val seconds =
            (clock.now().toEpochMilliseconds() - pausedAt.toEpochMilliseconds()) / 1000L
        if (seconds > 0L) sessionDao.addPausedSeconds(sessionId, seconds)
        sessionDao.setManualPauseStartedAt(sessionId, null)
    }

    // Clear the manual-pause marker without crediting the elapsed span to
    // pausedSeconds. Used only by cold-boot recovery when it resumes a
    // session whose process died while paused — the dead-process minutes
    // aren't real pause time and crediting them would distort the duration.
    suspend fun clearManualPause(sessionId: Long) {
        sessionDao.setManualPauseStartedAt(sessionId, null)
    }

    // Cold-boot recovery. Called by the Application class at startup:
    // if a row is still ACTIVE, the process must have died mid-session.
    // Returns the row so the Application can decide whether to resume the
    // foreground service or finalize the orphan.
    suspend fun resumeActiveIfAny(): Session? = sessionDao.getActiveSession()

    // Finalize a session whose process died mid-recording, once the
    // Application has decided the car is no longer moving (stationary, no
    // GPS fix, or location permission gone). Unlike stopSession, the
    // honest end time is the LAST GPS point that actually got recorded
    // before the process died — using clock.now() here would invent all
    // the minutes (or hours) the phone sat with the app dead, inflating
    // the duration into nonsense. A session with no points or no real
    // distance is discarded like any other empty drive.
    suspend fun finalizeInterruptedSession(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.state != SessionState.ACTIVE) return
        val lastPoint = gpsPointDao.latestForSession(sessionId)
        if (lastPoint == null || session.distanceMeters == 0.0) {
            sessionDao.deleteById(sessionId)
            return
        }
        val durationSeconds =
            (lastPoint.timestamp.toEpochMilliseconds() - session.startedAt.toEpochMilliseconds()) / 1000L -
                session.pausedSeconds
        sessionDao.finalize(
            id = sessionId,
            state = SessionState.DRAFT,
            endedAt = lastPoint.timestamp,
            durationSeconds = durationSeconds.coerceAtLeast(0L),
            endLat = lastPoint.lat,
            endLng = lastPoint.lng,
        )
        generateRouteSnapshot(sessionId)
    }

    // Delete one finished session that the user removed from the detail
    // screen. The session's GPS points cascade out with the row (gps_point
    // has onDelete = CASCADE), and the route-thumbnail WebP is removed so
    // the cache doesn't keep a file for a session that no longer exists. The
    // decoded in-memory thumbnail is evicted by the caller (AppContainer),
    // which owns that cache.
    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
        RouteSnapshot.delete(cacheDir, sessionId)
    }

    // Generate the route-thumbnail WebP for a single session. Called
    // automatically after finalization, and also by the one-time backfill
    // migration for sessions that predate this feature.
    private suspend fun generateRouteSnapshot(sessionId: Long) {
        if (RouteSnapshot.exists(cacheDir, sessionId)) return
        val points = gpsPointDao.pointsForSession(sessionId)
        val bitmap = RouteSnapshot.render(points) ?: return
        RouteSnapshot.save(cacheDir, sessionId, bitmap)
        bitmap.recycle()
    }

    // Backfill route snapshots for sessions recorded before the snapshot
    // feature landed. Fast no-op when all sessions already have one.
    suspend fun generateMissingSnapshots() {
        for (session in sessionDao.getAll()) {
            generateRouteSnapshot(session.id)
        }
    }

    // One-time data repair. Recomputes
    // distanceMeters for every session by replaying its stored GPS points
    // through a fresh SessionLifecycle. This fixes sessions recorded
    // before the distance-accumulation fix, where the 20 m movement
    // threshold discarded nearly all real distance. Replaying the exact
    // live classifier guarantees the recovered total matches what a new
    // recording of the same trip would now produce. Deterministic and
    // idempotent: safe to run on already-correct sessions.
    suspend fun recomputeAllDistances() {
        for (session in sessionDao.getAll()) {
            val points = gpsPointDao.pointsForSession(session.id)
            if (points.isEmpty()) continue
            val lifecycle = SessionLifecycle(session.startedAt)
            var total = 0.0
            for (point in points) {
                val decision = lifecycle.onSample(point)
                if (decision is Decision.Accept) total += decision.distanceDelta
            }
            sessionDao.setDistance(session.id, total)
        }
    }

    // One-time cleanup of empty (zero-distance) sessions recorded before
    // stopSession learned to discard them. Idempotent: once the rows are
    // gone, re-running deletes nothing. The live ACTIVE row is excluded by
    // the DAO query, so this is safe to run mid-session.
    suspend fun deleteZeroDistanceSessions() {
        sessionDao.deleteZeroDistanceSessions()
    }
}

// Re-exported alias so callers can import the Decision they're going
// to construct without also importing SessionLifecycle. Keeps call
// sites cleaner when wiring the service in commit 4.
typealias SampleDecision = Decision.Accept
