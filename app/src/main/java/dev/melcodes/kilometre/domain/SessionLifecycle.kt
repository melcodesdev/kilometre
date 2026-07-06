package dev.melcodes.kilometre.domain

import dev.melcodes.kilometre.domain.models.GpsPoint
import kotlinx.datetime.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Per-session stateful classifier. One instance lives for the duration
// of an active recording, owned by the foreground service. Each GPS
// sample arriving from FusedLocationProviderClient is fed through
// `onSample` and the returned `Decision` tells the caller exactly what
// to do: store the sample (with how much distance to add to the session
// row), enter or exit auto-pause, or fire auto-stop.
//
// The classifier is pure Kotlin with no Android imports. It can be
// unit-tested by replaying synthetic GPS sequences and asserting on
// the Decision stream.
//
// State machine, revised for the "distance undercount" fix:
//   - "Moving" is decided per-sample from GPS speed: speedMps >= 0.5 m/s
//     (~1.8 km/h). When speed is absent (some devices omit it) we fall
//     back to a displacement jitter floor of 3 m between good fixes.
//   - Distance accumulates the full haversine displacement on every
//     moving sample. It does NOT use a movement threshold — that was the
//     v1 bug: at 1 Hz, city-speed samples are ~8-14 m apart, under the
//     old 20 m threshold, so almost all real distance was discarded.
//   - Movement-based pause: 5 min with no moving sample triggers Paused.
//     The first moving sample clears it. Auto-stop fires after 90 min
//     paused continuously.
//
// Why speed and not position-delta for the moving test: while parked,
// GPS position wanders a few metres per sample (jitter), but the
// Doppler-derived speed stays near zero. Gating on speed means a long
// stop adds zero phantom distance, while genuine slow creep (which has
// real speed) still counts. Validated against a real 51-min drive:
// 17.9 km via this method vs 1.0 km from the v1 threshold bug.
//
// Accuracy filter: samples with accuracyMeters > 50 are still STORED by
// the caller (so the route polyline is honest) but are ignored here —
// they neither move the reference nor contribute distance, so a single
// bad GPS spike can't inject bogus kilometres.
class SessionLifecycle(
    private val sessionStartedAt: Instant,
    private val movingSpeedFloorMps: Float = MOVING_SPEED_FLOOR_MPS,
    private val distanceJitterFloorMeters: Double = DISTANCE_JITTER_FLOOR_M,
    private val pauseThresholdMs: Long = PAUSE_THRESHOLD_MS,
    private val autoStopThresholdMs: Long = AUTO_STOP_THRESHOLD_MS,
    private val accuracyRejectMeters: Float = ACCURACY_REJECT_M,
) {
    // The last good-accuracy sample, used as the anchor for the next
    // haversine displacement. Always advanced to the latest good fix
    // (even while stopped) so movement is measured fresh and a stale
    // anchor can't produce a giant jump when the car pulls away.
    private var lastReference: GpsPoint? = null

    // The timestamp of the last sample classified as moving. Initialised
    // to session start so we don't immediately pause on first sample.
    private var lastMovementAt: Instant = sessionStartedAt

    // When the current pause window started, or null if not paused.
    private var pauseStartedAt: Instant? = null

    // Decide whether a sample represents real motion. Speed is the
    // primary signal (reliable, near-zero while parked); the jitter
    // floor is the fallback for devices that don't report speed.
    private fun isMoving(speedMps: Float?, displacementMeters: Double): Boolean =
        if (speedMps != null) {
            speedMps >= movingSpeedFloorMps
        } else {
            displacementMeters >= distanceJitterFloorMeters
        }

    // Feed one GPS sample. Returns a Decision describing what the
    // caller should write to the DB and whether to fire auto-stop.
    fun onSample(sample: GpsPoint): Decision {
        val accuracyAcceptable = sample.accuracyMeters <= accuracyRejectMeters

        if (lastReference == null) {
            // First sample of the session. Nothing to measure from yet;
            // a good-accuracy sample becomes the first reference.
            if (accuracyAcceptable) {
                lastReference = sample
                lastMovementAt = sample.timestamp
            }
            return Decision.Accept(distanceDelta = 0.0, pauseEvent = null, autoStop = false)
        }

        if (!accuracyAcceptable) {
            // Ignore the bad fix entirely: don't move the reference, don't
            // add distance, don't touch pause state. The next good fix
            // measures across the gap correctly.
            return Decision.Accept(distanceDelta = 0.0, pauseEvent = null, autoStop = false)
        }

        val previous = lastReference!!
        val displacement = haversineMeters(previous.lat, previous.lng, sample.lat, sample.lng)
        lastReference = sample  // advance to the latest good fix

        val moving = isMoving(sample.speedMps, displacement)
        val currentlyPaused = pauseStartedAt != null

        if (moving) {
            // Real movement: add the full displacement and, if we were
            // paused, close the pause window.
            val pauseEvent = if (currentlyPaused) {
                val pauseDurationMs = sample.timestamp.toEpochMilliseconds() -
                    pauseStartedAt!!.toEpochMilliseconds()
                pauseStartedAt = null
                PauseEvent.Exited(pauseDurationMs)
            } else {
                null
            }
            lastMovementAt = sample.timestamp
            return Decision.Accept(distanceDelta = displacement, pauseEvent = pauseEvent, autoStop = false)
        }

        // Stationary sample: never adds distance.
        val msSinceLastMovement =
            sample.timestamp.toEpochMilliseconds() - lastMovementAt.toEpochMilliseconds()

        if (!currentlyPaused && msSinceLastMovement >= pauseThresholdMs) {
            // Just crossed into Paused. Anchor pauseStartedAt at the
            // moment we last had movement plus the threshold, not at
            // "now" — otherwise a long sample gap would falsely shrink
            // the recorded pause duration.
            val pauseStart = Instant.fromEpochMilliseconds(
                lastMovementAt.toEpochMilliseconds() + pauseThresholdMs
            )
            pauseStartedAt = pauseStart
            return Decision.Accept(
                distanceDelta = 0.0,
                pauseEvent = PauseEvent.Entered(pauseStart),
                autoStop = false,
            )
        }

        if (currentlyPaused) {
            val pauseDurationMs = sample.timestamp.toEpochMilliseconds() -
                pauseStartedAt!!.toEpochMilliseconds()
            if (pauseDurationMs >= autoStopThresholdMs) {
                // Pause has lasted 90+ minutes — auto-stop the session.
                // The caller finalizes the row; pause state is irrelevant
                // after that.
                return Decision.Accept(distanceDelta = 0.0, pauseEvent = null, autoStop = true)
            }
        }

        return Decision.Accept(distanceDelta = 0.0, pauseEvent = null, autoStop = false)
    }

    // The cumulative paused time recorded so far, in milliseconds.
    // Useful for tests; the real session row tracks this in Room via
    // SessionDao.addPausedSeconds when each pause window closes.
    fun isPaused(): Boolean = pauseStartedAt != null

    companion object {
        // Speed at or above which a sample counts as moving. 0.5 m/s is
        // ~1.8 km/h — below walking pace, comfortably above parked GPS
        // speed noise (typically < 0.3 m/s with a good fix). The total
        // distance is insensitive to this exact value: on the validation
        // drive, floors from 0.3 to 1.0 m/s all give 17.87-17.93 km.
        const val MOVING_SPEED_FLOOR_MPS = 0.5f

        // Fallback when a device omits speed: a fix must be at least this
        // far from the previous good fix to count as movement, filtering
        // stationary jitter.
        const val DISTANCE_JITTER_FLOOR_M = 3.0

        const val PAUSE_THRESHOLD_MS = 5L * 60L * 1000L      // 5 min
        const val AUTO_STOP_THRESHOLD_MS = 90L * 60L * 1000L // 90 min
        const val ACCURACY_REJECT_M = 50f

        // Great-circle distance between two lat/lng pairs, in metres.
        // Standard haversine formula. Earth radius 6_371_000 m is a
        // mean value — good enough for distance accumulation at the
        // 1-Hz sample rate; the per-sample error is well under GPS
        // noise. We compute on every accepted sample, so this is a
        // hot path — kept allocation-free.
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadiusM = 6_371_000.0
            val lat1Rad = lat1.toRadians()
            val lat2Rad = lat2.toRadians()
            val dLat = (lat2 - lat1).toRadians()
            val dLon = (lon2 - lon1).toRadians()
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadiusM * c
        }

        private fun Double.toRadians(): Double = this * (Math.PI / 180.0)
    }
}

// What the lifecycle wants the caller to do with this sample.
// Phase 1 only ever emits Accept (we always insert a row). The Reject
// variant is reserved for future filters; keeping the sealed shape now
// means later additions don't break existing call sites.
sealed interface Decision {
    data class Accept(
        val distanceDelta: Double,
        val pauseEvent: PauseEvent?,
        val autoStop: Boolean,
    ) : Decision
}

// Pause boundary events. PauseEvent is null on samples that don't
// change the pause state (i.e. continuing-to-move or still-paused).
sealed interface PauseEvent {
    // The session just entered an auto-pause. `at` is the start of
    // the pause window, anchored at "last movement + threshold" not
    // "now", so a sample gap doesn't shrink the recorded pause.
    data class Entered(val at: Instant) : PauseEvent

    // The session just exited an auto-pause. `durationMs` is how
    // long the pause lasted, suitable for adding to Session.pausedSeconds.
    data class Exited(val durationMs: Long) : PauseEvent
}
