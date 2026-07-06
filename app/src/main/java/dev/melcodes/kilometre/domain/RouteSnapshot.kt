package dev.melcodes.kilometre.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.melcodes.kilometre.domain.models.GpsPoint
import java.io.File
import kotlin.math.cos

// Renders a lightweight route-polyline thumbnail from raw GPS points and
// persists it as WebP in the cache directory. Used for two things:
// (1) instant route previews on the Sessions list (no MapLibre needed),
// (2) a future placeholder on the detail screen while MapLibre boots.
//
// The render is a simple equirectangular projection with a cos(lat)
// correction so the route doesn't look horizontally stretched at French
// latitudes (~48 N). No map tiles, no labels — just the line on a
// transparent background. Fast enough to run inline after session
// finalization (~2-5 ms for 2000 points at 360x240).
object RouteSnapshot {

    private const val WIDTH = 360
    private const val HEIGHT = 240

    // Dark-theme primary, hardcoded so the snapshot can be generated
    // outside a Compose composition context. Matches Purple80 from the
    // theme (the route line's endColor on the live map).
    private const val LINE_COLOR: Int = 0xFFD0BCFF.toInt()

    // Darkened toward black by 65%, same as the live map's startColor.
    private const val LINE_START_COLOR: Int = 0xFF493F59.toInt()

    // Semi-transparent white outline drawn under the route line so it stays
    // visible on the dark card surface behind the now-transparent tile.
    private const val CASING_COLOR: Int = 0xB3FFFFFF.toInt()

    // Padding fraction: 10% of the bbox on each side keeps the route
    // off the very edges of the thumbnail.
    private const val PADDING = 0.10f

    private const val DIR_NAME = "route_thumbs"

    // Parse an AARRGGBB hex string (e.g. "FF493F59") to an Android ARGB int.
    // Falls back to the built-in default on any parse failure.
    private fun hexToArgb(hex: String, fallback: Int): Int = try {
        android.graphics.Color.parseColor("#$hex")
    } catch (_: IllegalArgumentException) {
        fallback
    }

    // Draw the route polyline into a Bitmap. Returns null if the list
    // has fewer than 2 points (nothing to draw).
    // startHex / endHex: AARRGGBB hex overrides. Null → built-in defaults.
    fun render(
        points: List<GpsPoint>,
        startHex: String? = null,
        endHex: String? = null,
    ): Bitmap? {
        if (points.size < 2) return null

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }

        // cos(centerLat) corrects for longitude degrees being narrower
        // at higher latitudes. Without this the route looks horizontally
        // stretched at 48 N.
        val centerLat = (minLat + maxLat) / 2.0
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.01)

        val lngRange = ((maxLng - minLng) * cosLat).coerceAtLeast(0.0001)
        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)

        // Fit the route into the viewport while preserving aspect ratio.
        val padX = lngRange * PADDING
        val padY = latRange * PADDING
        val totalW = lngRange + 2 * padX
        val totalH = latRange + 2 * padY

        // Scale so the route fills the larger axis, centered on the smaller.
        val scaleX = WIDTH / totalW
        val scaleY = HEIGHT / totalH
        val scale = minOf(scaleX, scaleY)
        val offsetX = (WIDTH - totalW * scale) / 2.0
        val offsetY = (HEIGHT - totalH * scale) / 2.0

        fun projectX(lng: Double): Float =
            (offsetX + ((lng - minLng) * cosLat + padX) * scale).toFloat()

        fun projectY(lat: Double): Float =
            (offsetY + (totalH - ((lat - minLat) + padY)) * scale).toFloat()

        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // Draw each segment with a colour interpolated from dark (start of
        // drive) to bright (end of drive), matching the live map gradient.
        val resolvedStart = if (startHex != null) hexToArgb(startHex, LINE_START_COLOR) else LINE_START_COLOR
        val resolvedEnd = if (endHex != null) hexToArgb(endHex, LINE_COLOR) else LINE_COLOR
        val lastIndex = points.size - 1

        // Casing: a wider, semi-transparent light outline drawn once under the
        // gradient. The thumbnail tile is transparent, so the line sits on the
        // card's dark surface where a dark gradient end (e.g. a near-black
        // colour) would otherwise vanish. The pale casing keeps it legible on
        // dark backgrounds and simply blends away on light ones.
        val casingPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            color = CASING_COLOR
        }
        val casingPath = Path().apply {
            moveTo(projectX(points[0].lng), projectY(points[0].lat))
            for (i in 1..lastIndex) {
                lineTo(projectX(points[i].lng), projectY(points[i].lat))
            }
        }
        canvas.drawPath(casingPath, casingPaint)

        for (i in 1..lastIndex) {
            val fraction = i.toFloat() / lastIndex
            paint.color = lerpArgb(resolvedStart, resolvedEnd, fraction)
            canvas.drawLine(
                projectX(points[i - 1].lng), projectY(points[i - 1].lat),
                projectX(points[i].lng), projectY(points[i].lat),
                paint,
            )
        }
        return bitmap
    }

    // Save a snapshot bitmap to the cache directory.
    fun save(cacheDir: File, sessionId: Long, bitmap: Bitmap) {
        val dir = File(cacheDir, DIR_NAME)
        dir.mkdirs()
        val file = File(dir, "$sessionId.webp")
        @Suppress("DEPRECATION") // WEBP_LOSSY needs API 30+, our minSdk.
        file.outputStream().buffered().use {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it)
        }
    }

    // Load a previously saved snapshot. Returns null if no file exists or
    // the decode fails.
    fun load(cacheDir: File, sessionId: Long): Bitmap? {
        val file = File(cacheDir, DIR_NAME + "/$sessionId.webp")
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    // Check whether a snapshot exists on disk without decoding it.
    fun exists(cacheDir: File, sessionId: Long): Boolean =
        File(cacheDir, DIR_NAME + "/$sessionId.webp").exists()

    // Remove a session's snapshot file when the session is deleted. No-op if
    // it was never generated; a failed delete just leaves a stale WebP that
    // the next gradient change wipes anyway, so the return is ignored.
    fun delete(cacheDir: File, sessionId: Long) {
        File(cacheDir, DIR_NAME + "/$sessionId.webp").delete()
    }

    // Linear interpolation between two ARGB colour ints.
    private fun lerpArgb(start: Int, end: Int, fraction: Float): Int {
        val sA = start ushr 24
        val sR = (start shr 16) and 0xFF
        val sG = (start shr 8) and 0xFF
        val sB = start and 0xFF
        val eA = end ushr 24
        val eR = (end shr 16) and 0xFF
        val eG = (end shr 8) and 0xFF
        val eB = end and 0xFF
        val a = (sA + (eA - sA) * fraction).toInt()
        val r = (sR + (eR - sR) * fraction).toInt()
        val g = (sG + (eG - sG) * fraction).toInt()
        val b = (sB + (eB - sB) * fraction).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
