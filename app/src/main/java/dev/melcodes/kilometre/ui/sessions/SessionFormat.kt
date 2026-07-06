package dev.melcodes.kilometre.ui.sessions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.melcodes.kilometre.R
import dev.melcodes.kilometre.domain.SessionLifecycle
import dev.melcodes.kilometre.domain.models.GpsPoint
import kotlinx.datetime.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Shared session-formatting helpers used by both the Sessions list and
// the session detail screen. Extracted from SessionsScreen once the
// detail screen became the second caller (before that, inlining was the
// right call). Everything is locale-aware via java.time — no hardcoded
// month names to translate; the kotlinx Instant is bridged through epoch
// millis into the device timezone.

// Medium localised date ("29 May 2026" / "29 mai 2026").
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

// Short localised clock time ("14:32" / "2:32 PM").
private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

internal fun formatDate(instant: Instant): String =
    java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)

internal fun formatTime(instant: Instant): String =
    java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)

// Filesystem-safe timestamp for export filenames ("2026-05-31_11-05").
// Deliberately NOT locale-formatted: localised dates carry spaces and the
// short time uses a colon, both of which are awkward (colons are outright
// illegal) in filenames and SAF display names. A fixed sortable pattern
// also keeps exported files in chronological order in a file manager.
private val fileTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

internal fun formatFileTimestamp(instant: Instant): String =
    java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
        .format(fileTimestampFormatter)

// "14:32 – 15:05" for a finished session. endedAt is nullable (a session
// still ACTIVE has no end yet); in that case show only the start time.
@Composable
internal fun formatTimeRange(startedAt: Instant, endedAt: Instant?): String =
    if (endedAt != null) {
        stringResource(R.string.session_time_range, formatTime(startedAt), formatTime(endedAt))
    } else {
        formatTime(startedAt)
    }

// Human duration from a second count: "1 h 23 min" past an hour,
// otherwise "23 min". @Composable because it pulls localised strings.
@Composable
internal fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.session_duration_hm, hours, minutes)
    } else {
        stringResource(R.string.session_duration_m, minutes)
    }
}

// Average speed over the session's moving time. durationSeconds already
// excludes paused windows, so this is the real driving average, not a
// door-to-door figure. Guards a zero duration (would divide by zero on a
// just-finalised row before duration is written).
@Composable
internal fun formatAvgSpeed(distanceMeters: Double, durationSeconds: Long): String {
    val kmh = if (durationSeconds > 0) (distanceMeters / durationSeconds) * 3.6 else 0.0
    return stringResource(R.string.session_avg_speed, kmh)
}

// Top (maximum) recorded speed over a session, in km/h, derived from the raw
// GPS samples' Doppler speed. Not a stored column — the detail screen already
// has the points in memory for the map, and computing it here works on every
// existing session with no migration. Samples with poor accuracy (above the
// same threshold SessionLifecycle rejects for distance) or no speed reading
// are skipped, so one bad fix can't invent a spike. Returns null when no
// sample qualifies (e.g. an old track that never logged speed), letting the
// caller drop the row entirely. Plain function, not @Composable: the caller
// needs the nullable value to decide whether to show the stat at all.
internal fun topSpeedKmh(points: List<GpsPoint>): Double? {
    val maxMps = points
        .filter { it.accuracyMeters <= SessionLifecycle.ACCURACY_REJECT_M }
        .mapNotNull { it.speedMps }
        .maxOrNull()
        ?: return null
    return maxMps * 3.6
}

// An elevation profile ready to chart: each entry is (metres travelled so
// far, smoothed altitude in metres), plus the smoothed min/max for the axis
// labels and the total route length for the x-axis.
internal data class ElevationProfile(
    val samples: List<Pair<Float, Float>>,
    val minAltitude: Float,
    val maxAltitude: Float,
    val totalDistanceMeters: Float,
)

// Builds the elevation profile from the GPS samples already loaded for the
// map — on-device only, no external elevation service (sending the route to
// an online DEM would leak it off the phone). Phone GNSS altitude is noisy
// per fix, so the altitude track is smoothed with a centred moving average
// before charting and the min/max are read off the SMOOTHED series so the
// labels match the drawn line. Deliberately does NOT compute cumulative
// ascent/descent: summing raw GPS altitude noise inflates the total several
// fold (~1000 m here against a ~170 m truth), and only a real DEM like SRTM
// gives a trustworthy figure. Distances use Android's geodesic helper.
// Returns null when too few altitude samples exist (an old track that never
// logged altitude), so the caller drops the whole elevation card.
internal fun elevationProfile(points: List<GpsPoint>, half: Int = 15): ElevationProfile? {
    val pts = points.filter {
        it.accuracyMeters <= SessionLifecycle.ACCURACY_REJECT_M && it.altitudeMeters != null
    }
    if (pts.size < 10) return null

    val alts = FloatArray(pts.size) { pts[it].altitudeMeters!!.toFloat() }

    // Centred moving average. `half` is the window half-width in samples (≈
    // seconds at 1 Hz), from the Altitude smoothing setting: bigger removes
    // more per-fix GNSS noise while preserving the real climb/descent shape.
    val smooth = FloatArray(alts.size) { i ->
        val lo = maxOf(0, i - half)
        val hi = minOf(alts.size - 1, i + half)
        var sum = 0f
        for (j in lo..hi) sum += alts[j]
        sum / (hi - lo + 1)
    }

    // Cumulative geodesic distance for the x-axis.
    val cumulative = FloatArray(pts.size)
    val out = FloatArray(1)
    for (i in 1 until pts.size) {
        android.location.Location.distanceBetween(
            pts[i - 1].lat, pts[i - 1].lng, pts[i].lat, pts[i].lng, out,
        )
        cumulative[i] = cumulative[i - 1] + out[0]
    }

    return ElevationProfile(
        samples = pts.indices.map { cumulative[it] to smooth[it] },
        minAltitude = smooth.min(),
        maxAltitude = smooth.max(),
        totalDistanceMeters = cumulative.last(),
    )
}

// A speed-over-distance profile ready to chart alongside the elevation curve.
// Same x-axis (cumulative geodesic distance) and same smoothing window as the
// elevation profile, so the two align perfectly when overlaid. Speed comes
// from the GNSS Doppler reading on each fix; samples without a speed value or
// with poor accuracy are excluded. Returns null when the track carries no
// speed data (very old recordings before speedMps was logged).
internal data class SpeedProfile(
    val samples: List<Pair<Float, Float>>,  // (distanceMeters, speedKmh)
    val minSpeed: Float,
    val maxSpeed: Float,
    val totalDistanceMeters: Float,
)

internal fun speedProfile(points: List<GpsPoint>, half: Int = 7): SpeedProfile? {
    val pts = points.filter {
        it.accuracyMeters <= SessionLifecycle.ACCURACY_REJECT_M && it.speedMps != null
    }
    if (pts.size < 10) return null

    val speeds = FloatArray(pts.size) { pts[it].speedMps!! * 3.6f }

    // Same centred moving average as elevationProfile; `half` (window half-width
    // in samples ≈ seconds at 1 Hz) comes from the Speed smoothing setting.
    val smooth = FloatArray(speeds.size) { i ->
        val lo = maxOf(0, i - half)
        val hi = minOf(speeds.size - 1, i + half)
        var sum = 0f
        for (j in lo..hi) sum += speeds[j]
        sum / (hi - lo + 1)
    }

    val cumulative = FloatArray(pts.size)
    val out = FloatArray(1)
    for (i in 1 until pts.size) {
        android.location.Location.distanceBetween(
            pts[i - 1].lat, pts[i - 1].lng, pts[i].lat, pts[i].lng, out,
        )
        cumulative[i] = cumulative[i - 1] + out[0]
    }

    return SpeedProfile(
        samples = pts.indices.map { cumulative[it] to smooth[it] },
        minSpeed = smooth.min(),
        maxSpeed = smooth.max(),
        totalDistanceMeters = cumulative.last(),
    )
}
