package dev.melcodes.kilometre.domain

import dev.melcodes.kilometre.domain.models.GpsPoint
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Unit tests for SessionLifecycle distance accumulation. These exist
// because v1 shipped a bug: distance was gated by a 20 m movement
// threshold, so at 1 Hz nearly all real distance (samples ~8-14 m apart
// at city speed) was discarded — a 51-min, 18 km drive logged 1 km.
// The regression test below would have caught it.
class SessionLifecycleTest {

    private val start = Instant.fromEpochMilliseconds(1_000_000_000_000L)

    // Metres-per-degree of latitude at ~48.4°N (a mid-latitude). Used
    // to synthesise points a known distance apart by stepping latitude.
    private val metersPerDegLat = 111_195.0

    private fun replay(points: List<GpsPoint>): Double {
        val lifecycle = SessionLifecycle(start)
        var total = 0.0
        for (p in points) {
            val d = lifecycle.onSample(p)
            if (d is Decision.Accept) total += d.distanceDelta
        }
        return total
    }

    // Build a straight north-bound track: `count` points, `stepMeters`
    // apart, one second apart, with the given speed and accuracy.
    private fun straightTrack(
        count: Int,
        stepMeters: Double,
        speedMps: Float?,
        accuracyMeters: Float = 5f,
    ): List<GpsPoint> {
        val dLat = stepMeters / metersPerDegLat
        return (0 until count).map { i ->
            GpsPoint(
                sessionId = 1L,
                timestamp = Instant.fromEpochMilliseconds(start.toEpochMilliseconds() + i * 1000L),
                lat = 48.4 + i * dLat,
                lng = 2.7,
                accuracyMeters = accuracyMeters,
                speedMps = speedMps,
            )
        }
    }

    @Test
    fun cityDriving_accumulatesFullDistance() {
        // 100 hops of ~14 m at 14 m/s (~50 km/h) — every hop is BELOW the
        // old 20 m threshold, which is exactly the case v1 dropped.
        val points = straightTrack(count = 101, stepMeters = 14.0, speedMps = 14f)
        val total = replay(points)
        // 100 deltas of ~14 m ≈ 1400 m. Allow for haversine rounding.
        assertTrue("expected ~1400 m, got $total", total in 1380.0..1420.0)
    }

    @Test
    fun parkedJitter_addsNoDistance() {
        // Stationary: tiny position wander but speed pinned at zero. The
        // speed gate must reject every sample, so distance stays exactly 0.
        val points = (0 until 200).map { i ->
            GpsPoint(
                sessionId = 1L,
                timestamp = Instant.fromEpochMilliseconds(start.toEpochMilliseconds() + i * 1000L),
                lat = 48.4 + (if (i % 2 == 0) 0.00002 else -0.00002), // ~2 m wander
                lng = 2.7,
                accuracyMeters = 5f,
                speedMps = 0f,
            )
        }
        assertEquals(0.0, replay(points), 0.0)
    }

    @Test
    fun badAccuracySamples_areIgnored() {
        // Moving fast but every fix is junk (accuracy > 50 m): contributes
        // nothing, so a GPS spike can't inject phantom kilometres.
        val points = straightTrack(count = 101, stepMeters = 14.0, speedMps = 14f, accuracyMeters = 100f)
        assertEquals(0.0, replay(points), 0.0)
    }

    @Test
    fun missingSpeed_fallsBackToJitterFloor() {
        // Device omits speed: hops of 14 m exceed the 3 m jitter floor, so
        // distance still accumulates via the fallback path.
        val points = straightTrack(count = 101, stepMeters = 14.0, speedMps = null)
        val total = replay(points)
        assertTrue("expected ~1400 m, got $total", total in 1380.0..1420.0)
    }

    @Test
    fun missingSpeed_subFloorHops_areDropped() {
        // Without speed, hops under the jitter floor (here ~1 m) read as
        // jitter and are dropped — the accepted cost of having no speed.
        val points = straightTrack(count = 101, stepMeters = 1.0, speedMps = null)
        assertEquals(0.0, replay(points), 0.0)
    }
}
