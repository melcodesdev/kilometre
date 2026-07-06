package dev.melcodes.kilometre.ui.sessions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.melcodes.kilometre.DEFAULT_CHART_ALTITUDE_HEX
import dev.melcodes.kilometre.DEFAULT_CHART_SPEED_HEX
import dev.melcodes.kilometre.DEFAULT_GRADIENT_END_HEX
import dev.melcodes.kilometre.DEFAULT_GRADIENT_START_HEX
import dev.melcodes.kilometre.DEFAULT_REPLAY_DOT_HEX
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import dev.melcodes.kilometre.domain.models.GpsPoint
import kotlin.math.roundToInt
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.material3.ScaleBar
import org.maplibre.compose.material3.ScaleBarMeasure
import org.maplibre.compose.material3.ScaleBarMeasures
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import java.io.File

// Session detail screen. Opened by tapping a row on the Sessions tab.
// Shows a stats panel and the route map. Reads the session and its GPS
// points as Flows so the panel stays correct if the row changes.
// Map style and route gradient are sourced from the user's preferences
// so they immediately reflect any Settings change.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val session by container.sessionRepository.session(sessionId)
        .collectAsStateWithLifecycle(initialValue = null)
    // The recorded track for the route map. Collected at the top level
    // (not inside the s != null branch) so the Flow subscription is stable
    // across recompositions. Empty until Room emits.
    val points by container.sessionRepository.points(sessionId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Appearance preferences — map tile style and route gradient colours.
    val mapStyleName by container.mapStyle.collectAsStateWithLifecycle(initialValue = "liberty")
    val startHex by container.gradientStartHex.collectAsStateWithLifecycle(initialValue = DEFAULT_GRADIENT_START_HEX)
    val endHex by container.gradientEndHex.collectAsStateWithLifecycle(initialValue = DEFAULT_GRADIENT_END_HEX)

    // Style URL from the selected style name. Pattern matches OpenFreeMap's
    // hosted styles; "liberty" is the default that was previously hardcoded.
    val styleUrl = "https://tiles.openfreemap.org/styles/$mapStyleName"

    // Parse once per pref change so downstream composables receive typed
    // Color values rather than raw hex strings.
    val startColor = remember(startHex) { hexToComposeColor(startHex) }
    val endColor = remember(endHex) { hexToComposeColor(endHex) }

    // Replay & chart preferences (Settings → Replay / Chart).
    val replayDurationSec by container.replayDurationSeconds.collectAsStateWithLifecycle(initialValue = 20)
    val replayScaleToDistance by container.replayScaleToDistance.collectAsStateWithLifecycle(initialValue = false)
    val replayFollowDefault by container.replayFollowDefault.collectAsStateWithLifecycle(initialValue = false)
    val replayDefaultSpeed by container.replayDefaultSpeed.collectAsStateWithLifecycle(initialValue = 1f)
    val replayLoop by container.replayLoop.collectAsStateWithLifecycle(initialValue = false)
    val altitudeSmoothingHalf by container.altitudeSmoothingHalf.collectAsStateWithLifecycle(initialValue = 15)
    val speedSmoothingHalf by container.speedSmoothingHalf.collectAsStateWithLifecycle(initialValue = 7)
    val chartShowGrid by container.chartShowGrid.collectAsStateWithLifecycle(initialValue = true)
    val chartSpeedHex by container.chartSpeedHex.collectAsStateWithLifecycle(initialValue = DEFAULT_CHART_SPEED_HEX)
    val replayDotHex by container.replayDotHex.collectAsStateWithLifecycle(initialValue = DEFAULT_REPLAY_DOT_HEX)
    val chartAltitudeHex by container.chartAltitudeHex.collectAsStateWithLifecycle(initialValue = DEFAULT_CHART_ALTITUDE_HEX)
    val speedColor = remember(chartSpeedHex) { hexToComposeColor(chartSpeedHex) }
    val dotColor = remember(replayDotHex) { hexToComposeColor(replayDotHex) }
    val altitudeColor = remember(chartAltitudeHex) { hexToComposeColor(chartAltitudeHex) }

    val scope = rememberCoroutineScope()

    // The user's quick-save folder (a SAF tree URI), if they've set one in
    // Settings. Null hides the "Quick save" menu entry.
    val saveTreeUri by container.defaultSaveTreeUri.collectAsStateWithLifecycle(initialValue = null)

    // Persisted chart series selection, shared by the static elevation card and
    // the replay drawer's chart, so the user's choice (e.g. both altitude AND
    // speed) is remembered across closing the drawer, the screen, and restarts.
    val chartShowAltitude by container.chartShowAltitude.collectAsStateWithLifecycle(initialValue = true)
    val chartShowSpeed by container.chartShowSpeed.collectAsStateWithLifecycle(initialValue = false)
    val setChartShowAltitude: (Boolean) -> Unit = { scope.launch { container.setChartShowAltitude(it) } }
    val setChartShowSpeed: (Boolean) -> Unit = { scope.launch { container.setChartShowSpeed(it) } }

    // "Save to device" launcher: opens the system create-document dialog so
    // the user chooses where and under what name the GPX lands. The system
    // hands back a one-file writable URI — no storage permission needed. The
    // callback always sees the latest session/points (rememberLauncher keeps
    // the callback updated across recompositions).
    val saveLocallyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val s = session
        if (uri != null && s != null) {
            scope.launch {
                val ok = writeGpxToUri(context, uri, formatDate(s.startedAt), points)
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.export_saved else R.string.export_save_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                // The outer AppShell Scaffold already pads this screen down by
                // the status-bar height, so letting the TopAppBar apply its
                // default status-bar inset too would double the top gap.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(session?.let { formatDate(it.startedAt) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    val s = session
                    // Delete is offered for any loaded session, even one with
                    // no route points (a near-zero junk drive) — that is exactly
                    // the kind of row the user needs to be able to remove. Guarded
                    // by a confirmation dialog because it cascade-deletes the GPS
                    // points and can't be undone.
                    if (s != null) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.cd_delete_session),
                            )
                        }
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text(stringResource(R.string.session_delete_title)) },
                                text = { Text(stringResource(R.string.session_delete_body)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showDeleteConfirm = false
                                        scope.launch {
                                            // Delete first, then navigate back. The
                                            // session Flow will emit null, but we're
                                            // leaving the screen so it never shows.
                                            container.deleteSession(s.id)
                                            onBack()
                                        }
                                    }) {
                                        Text(stringResource(R.string.session_delete_confirm))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text(stringResource(R.string.dialog_cancel))
                                    }
                                },
                            )
                        }
                    }
                    // Export is only enabled once the session row and points
                    // are loaded — an empty points list produces an empty GPX
                    // which is useless to share or save.
                    if (s != null && points.isNotEmpty()) {
                        // The share icon now opens a small menu anchored under
                        // it (Box gives the DropdownMenu that anchor) instead
                        // of firing the share sheet directly, so the user can
                        // choose share vs. save vs. quick-save.
                        var menuExpanded by remember { mutableStateOf(false) }
                        val tree = saveTreeUri
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = stringResource(R.string.cd_export_gpx),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_menu_share)) },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            exportSessionAsGpx(
                                                context = context,
                                                fileName = "session_${formatFileTimestamp(s.startedAt)}.gpx",
                                                sessionDate = formatDate(s.startedAt),
                                                points = points,
                                            )
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_menu_save)) },
                                    onClick = {
                                        menuExpanded = false
                                        // launch() input is the suggested
                                        // filename; the user can rename it, and
                                        // the system picker auto-appends "(1)"
                                        // if the chosen folder already has it.
                                        saveLocallyLauncher.launch("session_${formatFileTimestamp(s.startedAt)}.gpx")
                                    },
                                )
                                // Only offered when a default folder is set.
                                if (tree != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_menu_quick_save)) },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch {
                                                val folder = quickSaveGpx(
                                                    context = context,
                                                    treeUriString = tree,
                                                    baseName = "session_${formatFileTimestamp(s.startedAt)}",
                                                    sessionDate = formatDate(s.startedAt),
                                                    points = points,
                                                )
                                                Toast.makeText(
                                                    context,
                                                    if (folder != null) {
                                                        context.getString(R.string.export_quick_saved, folder)
                                                    } else {
                                                        context.getString(R.string.export_save_failed)
                                                    },
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val s = session
        if (s == null) {
            // Either the first emission hasn't arrived yet, or the row was
            // deleted while the screen was open. Phase 2 doesn't bother
            // distinguishing the two — both show the same neutral message.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.session_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        // Load the custom vector drawables once per composition. Each stat row
        // gets its own icon so the card doesn't read as the same glyph recoloured:
        // ruler for distance, stopwatch for elapsed duration, clock for wall-time,
        // gauge for average speed, lightning bolt for top speed.
        val distanceIcon = ImageVector.vectorResource(R.drawable.ic_distance)
        val durationIcon = ImageVector.vectorResource(R.drawable.ic_duration)
        val clockIcon = ImageVector.vectorResource(R.drawable.ic_timer)
        val speedIcon = ImageVector.vectorResource(R.drawable.ic_speed)
        val boltIcon = ImageVector.vectorResource(R.drawable.ic_bolt)
        val pauseIcon = ImageVector.vectorResource(R.drawable.ic_pause)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Stats card ───────────────────────────────────────────────
            // All numeric facts about the drive grouped into one tonal card,
            // matching the Settings screen's visual language (section header +
            // surfaceContainer card + icon-badged rows with dividers).
            StatGroup(stringResource(R.string.session_section_stats)) {
                // Three-colour semantic scheme:
                //   Blue  — distance and speed (motion / ground coverage)
                //   Purple — time (duration, clock, paused)
                //   Slate  — currently unused; kept for future neutral rows
                StatItemRow(
                    icon = distanceIcon,
                    accent = StatAccentBlue,
                    label = stringResource(R.string.session_stat_distance),
                    value = stringResource(R.string.session_distance, s.distanceMeters / 1000.0),
                )
                StatRowDivider()
                StatItemRow(
                    icon = durationIcon,
                    accent = StatAccentPurple,
                    label = stringResource(R.string.session_stat_duration),
                    value = formatDuration(s.durationSeconds),
                )
                StatRowDivider()
                StatItemRow(
                    icon = clockIcon,
                    accent = StatAccentPurple,
                    label = stringResource(R.string.session_stat_time),
                    value = formatTimeRange(s.startedAt, s.endedAt),
                )
                StatRowDivider()
                StatItemRow(
                    icon = speedIcon,
                    accent = StatAccentBlue,
                    label = stringResource(R.string.session_stat_avg_speed),
                    value = formatAvgSpeed(s.distanceMeters, s.durationSeconds),
                )
                // Top speed is derived from the GPS samples already loaded for
                // the map. Null means no qualifying speed reading — skip the row
                // rather than show a misleading 0.
                val topKmh = topSpeedKmh(points)
                if (topKmh != null) {
                    StatRowDivider()
                    StatItemRow(
                        icon = boltIcon,
                        accent = StatAccentBlue,
                        label = stringResource(R.string.session_stat_top_speed),
                        value = stringResource(R.string.session_avg_speed, topKmh),
                    )
                }
                // Only show a paused row when the drive actually paused.
                if (s.pausedSeconds > 0) {
                    StatRowDivider()
                    StatItemRow(
                        icon = pauseIcon,
                        accent = StatAccentPurple,
                        label = stringResource(R.string.session_stat_paused),
                        value = formatDuration(s.pausedSeconds),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Route card ───────────────────────────────────────────────
            // The map sits inside a StatGroup card so the route preview is
            // framed the same way as the stats above it. The StatGroup's
            // Surface clips the map to the same RoundedCornerShape(16.dp)
            // used everywhere — possible because TextureView render mode is
            // already set (SurfaceView would ignore Compose layer clipping).
            var showLegend by remember { mutableStateOf(false) }
            var showFullscreen by remember { mutableStateOf(false) }
            StatGroup(stringResource(R.string.session_section_route)) {
                RouteMap(
                    points = points,
                    styleUrl = styleUrl,
                    startColor = startColor,
                    endColor = endColor,
                    gestures = GestureOptions.AllDisabled,
                    onTap = { showFullscreen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                ) { _, _ ->
                    // Info button sits in the bottom-right corner of the map.
                    // The transparent tap-interceptor (inside RouteMap) sits
                    // under it, so this button's own taps don't trigger fullscreen.
                    FilledTonalIconButton(
                        onClick = { showLegend = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.30f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.cd_route_legend),
                        )
                    }
                }
            }
            if (showLegend) {
                RouteLegendDialog(
                    startColor = startColor,
                    endColor = endColor,
                    onDismiss = { showLegend = false },
                )
            }
            if (showFullscreen) {
                // Scale-to-distance: ~1 s per km, clamped 10–120 s, so long
                // drives aren't a blur; otherwise the chosen fixed seconds.
                val replayDurationMs = remember(replayDurationSec, replayScaleToDistance, session?.distanceMeters) {
                    if (replayScaleToDistance) {
                        val km = (session?.distanceMeters ?: 0.0) / 1000.0
                        (km * 1000.0).toInt().coerceIn(10_000, 120_000)
                    } else {
                        replayDurationSec * 1000
                    }
                }
                FullscreenRouteMap(
                    points = points,
                    styleUrl = styleUrl,
                    startColor = startColor,
                    endColor = endColor,
                    altitudeColor = altitudeColor,
                    speedColor = speedColor,
                    dotColor = dotColor,
                    showAltitude = chartShowAltitude,
                    onShowAltitudeChange = setChartShowAltitude,
                    showSpeed = chartShowSpeed,
                    onShowSpeedChange = setChartShowSpeed,
                    showGrid = chartShowGrid,
                    smoothingHalf = altitudeSmoothingHalf,
                    speedSmoothingHalf = speedSmoothingHalf,
                    replayDurationMs = replayDurationMs,
                    followDefault = replayFollowDefault,
                    defaultSpeed = replayDefaultSpeed,
                    loop = replayLoop,
                    onClose = { showFullscreen = false },
                )
            }

            // ── Elevation card ───────────────────────────────────────────
            // Altitude-vs-distance profile from our own recorded GPS altitude
            // (no online elevation lookup — that would leak the route). Only
            // shown when the track actually carries altitude samples. Smoothing
            // width comes from the Altitude smoothing setting.
            val elevation = remember(points, altitudeSmoothingHalf) {
                elevationProfile(points, altitudeSmoothingHalf)
            }
            val detailSpeedProfile = remember(points, speedSmoothingHalf) {
                speedProfile(points, speedSmoothingHalf)
            }
            if (elevation != null) {
                Spacer(Modifier.height(8.dp))
                StatGroup(stringResource(R.string.session_section_elevation)) {
                    ElevationChart(
                        profile = elevation,
                        altitudeColor = altitudeColor,
                        speedColor = speedColor,
                        showGrid = chartShowGrid,
                        speedProfile = detailSpeedProfile,
                        showAltitude = chartShowAltitude,
                        onShowAltitudeChange = setChartShowAltitude,
                        showSpeed = chartShowSpeed,
                        onShowSpeedChange = setChartShowSpeed,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// How long a full-route replay takes, end to end, regardless of how long the
// real drive was. The dot moves at constant *visual* speed (arc-length paced,
// see positionAt), so a 5-minute drive and a 50-minute one both replay in this
// many milliseconds — and a long parked gap mid-drive doesn't make the dot sit
// still. Tunable; ~20 s reads as a brisk fly-through without feeling rushed.
private const val REPLAY_DURATION_MS = 20000


// Forces the replay tween to run at full duration regardless of the system
// "animator duration scale" (Developer Options / accessibility "remove
// animations"). Compose's animateTo multiplies its duration by the
// MotionDurationScale in the coroutine context, which by default mirrors that
// system setting — so with animations off the 20 s tween collapses to 0 ms and
// the drive "replays" instantly. The replay is content the user explicitly
// asked to watch, not an incidental UI transition, so we opt it out by running
// the driver in a context that always reports a 1× scale.
private val FullDurationScale = object : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

// Holds everything the drive replay needs: the route as a list of GeoJSON
// positions, the cumulative geodesic distance to each vertex, the total
// length, the recorded speed at each vertex, and an Animatable carrying replay
// progress in 0..1. Built once per point list by rememberRouteReplay.
// Arc-length parametrisation (positionAt) is what makes the dot move at
// constant speed instead of lurching between sparsely-sampled fixes or freezing
// over a parked stretch.
internal class RouteReplayState(
    val positions: List<Position>,
    private val cumulative: FloatArray,
    val totalMeters: Float,
) {
    // Replay head, 0 at the start of the drive, 1 at the end. Driven by a
    // tween in FullscreenRouteMap; read by positionAt to place the dot.
    val progress = Animatable(0f)

    // Largest vertex index whose cumulative distance is still <= target metres.
    private fun indexAt(target: Float): Int {
        var lo = 0
        var hi = cumulative.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulative[mid] <= target) lo = mid else hi = mid - 1
        }
        return lo
    }

    // The lat/lng at normalised arc-length p, linearly interpolated between the
    // two GPS vertices that bracket p*totalMeters. Constant-speed by
    // construction: equal increments of p cover equal ground distance.
    fun positionAt(p: Float): Position {
        val target = p.coerceIn(0f, 1f) * totalMeters
        val lo = indexAt(target)
        if (lo >= positions.size - 1) return positions.last()
        val segLen = cumulative[lo + 1] - cumulative[lo]
        val t = if (segLen > 0f) (target - cumulative[lo]) / segLen else 0f
        val a = positions[lo]
        val b = positions[lo + 1]
        return Position(
            a.longitude + (b.longitude - a.longitude) * t,
            a.latitude + (b.latitude - a.latitude) * t,
        )
    }

}

// Precompute the replay state from the same point list the route line uses, so
// the dot rides exactly on the drawn line. Returns null for a track too short
// or with zero length (nothing to replay). Geodesic distances via Android's
// Location helper, the same maths as the elevation x-axis.
@Composable
private fun rememberRouteReplay(points: List<GpsPoint>): RouteReplayState? =
    remember(points) {
        if (points.size < 2) return@remember null
        val positions = points.map { Position(it.lng, it.lat) }
        val cumulative = FloatArray(positions.size)
        val out = FloatArray(1)
        for (i in 1 until positions.size) {
            android.location.Location.distanceBetween(
                points[i - 1].lat, points[i - 1].lng,
                points[i].lat, points[i].lng, out,
            )
            cumulative[i] = cumulative[i - 1] + out[0]
        }
        val total = cumulative.last()
        if (total <= 0f) null else RouteReplayState(positions, cumulative, total)
    }

// The same route map blown up to the whole screen with pan/pinch enabled,
// shown as a Dialog so it sits over the bottom nav too. usePlatformDefaultWidth
// = false lets it actually cover the device width instead of the
// AlertDialog-style inset. The close button dismisses; the FAB re-fits the
// camera to the route's bounding box after the user has panned/zoomed away.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenRouteMap(
    points: List<GpsPoint>,
    styleUrl: String,
    startColor: Color,
    endColor: Color,
    altitudeColor: Color,
    speedColor: Color,
    dotColor: Color,
    // Persisted chart series selection (hoisted to the detail screen so it is
    // remembered across opening/closing this drawer and the whole map).
    showAltitude: Boolean,
    onShowAltitudeChange: (Boolean) -> Unit,
    showSpeed: Boolean,
    onShowSpeedChange: (Boolean) -> Unit,
    // Settings-driven replay/chart behaviour.
    showGrid: Boolean,
    smoothingHalf: Int,
    speedSmoothingHalf: Int,
    replayDurationMs: Int,
    followDefault: Boolean,
    defaultSpeed: Float,
    loop: Boolean,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showLegend by remember { mutableStateOf(false) }
    if (showLegend) {
        RouteLegendDialog(
            startColor = startColor,
            endColor = endColor,
            onDismiss = { showLegend = false },
        )
    }

    // Drive-replay state. `replay` is null for a track too short to animate;
    // `replaying` toggles the transport UI and the dot layer; `playing` runs
    // the head forward.
    val replay = rememberRouteReplay(points)
    // Elevation and speed profiles for the chart box under the replay controls.
    // Both are null when the track lacks the relevant data (old recordings with
    // no altitude or no speed readings).
    val elevation = remember(points, smoothingHalf) { elevationProfile(points, smoothingHalf) }
    val replaySpeedProfile = remember(points, speedSmoothingHalf) {
        speedProfile(points, speedSmoothingHalf)
    }
    // Which series the chart box shows comes in as showAltitude/showSpeed params
    // (persisted at the detail-screen level), so the selection is remembered.
    var replaying by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    // Playback rate multiplier. 1f = the base REPLAY_DURATION_MS pace; 2f plays
    // the drive in half the time, 0.5f in double. Changing it re-keys the driver
    // below so the remaining tween is recomputed at the new rate mid-playback.
    var speed by remember { mutableFloatStateOf(defaultSpeed) }
    // Camera-follow toggle. When on, the map keeps the replay head centred (see
    // the follow effect below); off lets the camera sit wherever the user left
    // it. A button in the replay overlay flips it.
    var following by remember { mutableStateOf(false) }
    // Whether the playback-rate picker (0.25×–2×) row is expanded. Distinct from
    // the chart's showSpeed series toggle; named so they don't collide. Reset on
    // each replay-start so the picker always opens collapsed.
    var showSpeedPicker by remember { mutableStateOf(false) }
    // Measured height of the transport panel, in pixels. The camera uses it as
    // bottom inset padding so the route is framed in the visible area ABOVE the
    // panel — the map stays physically full size (no resize, no flash) but
    // behaves as if it were cropped to the space the panel leaves.
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // The live map zoom, mirrored from the camera so exitReplay can read it
    // without holding the CameraState (which lives down in RouteMap).
    var liveZoom by remember { mutableStateOf<Double?>(null) }
    // The zoom to restore next time the replay opens, set ONLY when the user was
    // tracking (following) the dot at close. Null otherwise, meaning "just
    // recenter on reopen". This is how a tracked zoom survives a close while a
    // plain zoom-in does not.
    var trackedZoom by remember { mutableStateOf<Double?>(null) }

    // Replay driver: while `playing`, run progress to 1 with a linear tween
    // whose duration is the slice of replayDurationMs still ahead of the current
    // head, divided by `speed` — so resuming after a pause or a scrub, or
    // changing the rate, keeps the overall pace. LinearEasing is deliberate: any
    // easing would accelerate/decelerate the dot at the ends, which reads as a
    // stutter. When `loop` is on the run repeats from the start instead of
    // stopping; otherwise reaching the end clears `playing`. Keyed on
    // replayDurationMs and loop so changing those settings takes effect live.
    LaunchedEffect(playing, speed, replayDurationMs, loop) {
        val r = replay
        if (playing && r != null) {
            do {
                if (r.progress.value >= 1f) r.progress.snapTo(0f)
                val remaining = ((1f - r.progress.value) * replayDurationMs / speed).toInt()
                // withContext(FullDurationScale): see FullDurationScale — without
                // it the tween honours the system animator scale and finishes
                // instantly when the user has animations disabled.
                withContext(FullDurationScale) {
                    r.progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = remaining.coerceAtLeast(1), easing = LinearEasing),
                    )
                }
            } while (loop)
            playing = false
        }
    }

    // Tear down a replay: flip `replaying` false (which plays the panel's
    // exit-slide via AnimatedVisibility) and stop the driver. Deliberately
    // does NOT reset the chart toggles (showSpeed / which series) here — doing
    // so would change the panel's height while it's sliding out and jolt the
    // animation. Those are reset on the next replay-start instead.
    val exitReplay = {
        // Remember the zoom to restore only if the user was tracking the dot;
        // a plain zoom-in (follow off) is forgotten, so reopening just recenters.
        trackedZoom = if (following) liveZoom else null
        replaying = false
        playing = false
        following = false
        speed = defaultSpeed
    }
    // Radius of the panel's rounded top corners.
    val panelCornerRadius = 20.dp

    // How far the panel is dragged down from its resting position, in px (0 =
    // fully open). The drag handle writes this live so the panel tracks the
    // finger and stays wherever it is held; on release it settles to 0 (snap
    // back open) or off-screen (close). Driven by an Animatable so the settle
    // is a smooth animation that can start from any held position.
    val panelDragY = remember { Animatable(0f) }
    // Animate the panel off-screen downward, then tear down the replay. Used by
    // both a tap on the handle and a drag past the close threshold.
    val closePanel: () -> Unit = {
        scope.launch {
            val h = panelHeightPx.toFloat().coerceAtLeast(1f)
            panelDragY.animateTo(h, tween(220, easing = FastOutSlowInEasing))
            exitReplay()           // replaying = false → panel leaves composition
            panelDragY.snapTo(0f)  // reset so the next open slides in from rest
        }
        Unit
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Translucent dark bubble for buttons that sit on the map surface.
        val overlayColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.30f),
            contentColor = Color.White,
        )
        // Box: the map fills the whole dialog and never resizes; the transport
        // panel is overlaid on top, aligned to the bottom, and slides in/out as
        // a solid block. Resizing a GL map surface mid-animation blanks a frame
        // (the white flash), so we deliberately keep the map a fixed size and
        // let the panel float over its lower edge instead.
        Box(modifier = Modifier.fillMaxSize()) {
        RouteMap(
            points = points,
            styleUrl = styleUrl,
            startColor = startColor,
            endColor = endColor,
            // Fixed full size — never resized, so the GL surface never blanks.
            // The panel overlays its lower edge; the panel's rounded corners
            // reveal these (full-size) map tiles, so no black shows through.
            modifier = Modifier.fillMaxSize(),
            mapContent = {
                val rDot = replay
                if (rDot != null && replaying) {
                    // Create the dot's source ONCE, then move it with a
                    // frame-synced setData loop instead of re-reading progress in
                    // composition. Reading progress here would recompose this map
                    // content — re-evaluating the route layers — on every frame,
                    // which is what stuttered the dot (badly on the debuggable
                    // build). The loop reads progress inside withFrameNanos, so the
                    // layers compose once and only the dot's geometry updates, one
                    // clean update per rendered frame, gliding along the line.
                    val dotSource = rememberGeoJsonSource(
                        remember(rDot) {
                            GeoJsonData.Features(Point(rDot.positionAt(rDot.progress.value)))
                        },
                    )
                    LaunchedEffect(rDot) {
                        while (true) {
                            withFrameNanos { }
                            dotSource.setData(
                                GeoJsonData.Features(Point(rDot.positionAt(rDot.progress.value))),
                            )
                        }
                    }
                    // In-map CircleLayer (NOT a Compose overlay): the dot lives on
                    // the map's render surface, so it stays glued to the tiles
                    // during pan/zoom and camera-follow. White casing under a
                    // coloured core so it reads on any tile.
                    CircleLayer(
                        id = "replay-dot-casing",
                        source = dotSource,
                        radius = const(9.dp),
                        color = const(Color.White),
                    )
                    CircleLayer(
                        id = "replay-dot",
                        source = dotSource,
                        radius = const(6.dp),
                        color = const(dotColor),
                    )
                }
            },
        ) { cameraState, bounds ->
            // Mirror the live zoom up to FullscreenRouteMap so exitReplay can
            // capture it on close.
            LaunchedEffect(cameraState) {
                snapshotFlow { cameraState.position.zoom }.collect { liveZoom = it }
            }
            // Camera behaviour on opening / closing the replay drawer:
            //  - Open + a tracked zoom saved (user was following last time):
            //    zoom back in to it and resume following.
            //  - Open fresh (no tracked zoom): frame the route above the panel.
            //  - Close: always recenter to the whole route (a chosen zoom does
            //    not linger; only a *tracked* one is remembered, via exitReplay).
            // Runs once per open/close (not on panel-height changes), so toggling
            // the speed picker no longer re-frames and stomps the view.
            LaunchedEffect(replaying, bounds) {
                if (bounds == null) return@LaunchedEffect
                if (replaying) {
                    // Pause follow during any zoom-in so the loop doesn't fight
                    // the animation; resume it once settled.
                    following = false
                    val z = trackedZoom
                    val r = replay
                    if (z != null && r != null) {
                        val dot = r.positionAt(r.progress.value)
                        // Re-apply the bottom inset here too. CameraPosition
                        // carries its padding and the follow loop preserves it via
                        // copy(), but the close reset it to 32dp all round — so
                        // without this the resumed follow would centre the dot in
                        // the whole map (behind the panel) instead of the visible
                        // area above it. panelHeightPx survives the close (the
                        // panel isn't re-measured to 0), so it's still valid.
                        val bottom = if (panelHeightPx > 0) {
                            with(density) { panelHeightPx.toDp() } + 24.dp
                        } else {
                            200.dp
                        }
                        cameraState.animateTo(
                            cameraState.position.copy(
                                target = Position(dot.longitude, dot.latitude),
                                zoom = z,
                                padding = PaddingValues(
                                    start = 24.dp, top = 24.dp, end = 24.dp, bottom = bottom,
                                ),
                            ),
                        )
                        following = true
                    } else {
                        // Wait for the panel to be measured, then fit above it.
                        val h = snapshotFlow { panelHeightPx }.first { it > 0 }
                        val bottom = with(density) { h.toDp() } + 24.dp
                        cameraState.animateTo(
                            bounds,
                            padding = PaddingValues(
                                start = 24.dp, top = 24.dp, end = 24.dp, bottom = bottom,
                            ),
                        )
                        // Honour the "start following" setting from the fitted view.
                        following = followDefault
                    }
                } else {
                    cameraState.animateTo(bounds, padding = PaddingValues(32.dp))
                }
            }
            FilledTonalIconButton(
                onClick = onClose,
                colors = overlayColors,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_fullscreen_map),
                )
            }
            // The map controls (info, recenter, compass, replay-start) own the
            // bottom edge only when not replaying; the transport bar takes it
            // over during playback so the two never overlap.
            if (!replaying) {
                // Info button sits where the MapLibre logo was (BottomStart).
                // The logo is disabled (BSD-3 has no UI logo requirement); OSM ODbL
                // attribution is in the RouteLegendDialog footer instead.
                FilledTonalIconButton(
                    onClick = { showLegend = true },
                    colors = overlayColors,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.cd_route_legend),
                    )
                }
                // Recenter only appears once bounds exist.
                if (bounds != null) {
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                cameraState.animateTo(bounds, padding = PaddingValues(48.dp))
                            }
                        },
                        colors = overlayColors,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = stringResource(R.string.cd_recenter_map),
                        )
                    }
                }
                // Compass: only shown once the user has rotated off north, so it
                // stays out of the way the rest of the time. The icon spins with
                // the map (rotate by -bearing) so it always points to true north;
                // tapping it animates the bearing back to 0. Sits just above the
                // recenter button in the same translucent style as the others.
                val bearing = cameraState.position.bearing
                if (bearing != 0.0) {
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                cameraState.animateTo(
                                    cameraState.position.copy(bearing = 0.0),
                                )
                            }
                        },
                        colors = overlayColors,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 64.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_compass),
                            contentDescription = stringResource(R.string.cd_compass_north),
                            // Unspecified keeps the needle's own red/white instead
                            // of flattening it to the button's white content colour.
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(30.dp)
                                .rotate(-bearing.toFloat()),
                        )
                    }
                }
                // Replay-start: kicks off playback. Bottom-centre so it doesn't
                // crowd the info/recenter corners. Only when there's a track.
                if (replay != null) {
                    FilledTonalIconButton(
                        onClick = {
                            // Open: speed picker collapsed, pace at the default
                            // speed setting. The camera effect (keyed on
                            // `replaying`) decides follow + zoom — resume-tracking
                            // if a tracked zoom was saved, else a fresh fit. The
                            // chart series selection is persisted so it's not reset.
                            showSpeedPicker = false
                            speed = defaultSpeed
                            replaying = true
                            playing = true
                        },
                        colors = overlayColors,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = stringResource(R.string.cd_replay_start),
                        )
                    }
                }
            }
            // Replay transport, stacked at the bottom edge while playback is
            // active: the elevation strip on top (its cursor doubles as the
            // scrubber — drag the dot to seek), then a YouTube-style transport
            // row: exit at the start, prev/play/next centred, speed at the end.
            // Camera-follow: per-frame exponential lerp toward the dot. The
            // replay covers the whole drive in a fixed 20 s, so the dot moves
            // fast across the map; a slow chase visibly trails it. k=20 (gap
            // half-life ~35 ms) tracks tightly — close to locked-on — while the
            // dt-scaled alpha still absorbs the odd dropped frame so it doesn't
            // judder. The smoothed centre also makes the resume after a pinch a
            // quick glide rather than a hard snap.
            val r = replay
            if (replaying && r != null) {
                LaunchedEffect(following) {
                    if (following) {
                        var smoothLat = cameraState.position.target.latitude
                        var smoothLng = cameraState.position.target.longitude
                        var prevNanos = withFrameNanos { it }
                        while (true) {
                            val frameNanos = withFrameNanos { it }
                            val dt = ((frameNanos - prevNanos) / 1_000_000_000.0)
                                .coerceIn(0.0, 0.05)
                            prevNanos = frameNanos
                            // While the user is actively pinch-zooming or
                            // panning, yield the camera entirely — writing
                            // position every frame here is what made zoom
                            // rubber-band. The library leaves moveReason stuck at
                            // GESTURE after the gesture ends and only clears
                            // isCameraMoving, so we require BOTH (reason == GESTURE
                            // AND still moving); the moment the finger lifts,
                            // isCameraMoving goes false and follow resumes from the
                            // user's current view and zoom. Keep the smoothed
                            // centre synced while yielding so the resume is seamless.
                            if (cameraState.moveReason == CameraMoveReason.GESTURE &&
                                cameraState.isCameraMoving
                            ) {
                                smoothLat = cameraState.position.target.latitude
                                smoothLng = cameraState.position.target.longitude
                                continue
                            }
                            val dot = r.positionAt(r.progress.value)
                            val alpha = 1.0 - kotlin.math.exp(-20.0 * dt)
                            smoothLat += (dot.latitude - smoothLat) * alpha
                            smoothLng += (dot.longitude - smoothLng) * alpha
                            cameraState.position = cameraState.position.copy(
                                target = Position(smoothLng, smoothLat),
                            )
                        }
                    }
                }
                // Follow toggle stacked under the Close button.
                FilledTonalIconButton(
                    onClick = { following = !following },
                    colors = if (following) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = StatAccentBlue,
                            contentColor = Color.White,
                        )
                    } else {
                        overlayColors
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 64.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_my_location),
                        contentDescription = stringResource(R.string.cd_replay_follow),
                    )
                }
                // Recenter, available during replay too — sits just above the
                // drawer (bottom padding = panel height) so it's tappable while
                // watching. Stops following and re-fits the route into the area
                // above the panel.
                if (bounds != null) {
                    val panelHeightDp = with(density) { panelHeightPx.toDp() }
                    FilledTonalIconButton(
                        onClick = {
                            following = false
                            scope.launch {
                                val inset = if (panelHeightPx > 0) panelHeightDp + 24.dp else 200.dp
                                cameraState.animateTo(
                                    bounds,
                                    padding = PaddingValues(
                                        start = 24.dp, top = 24.dp, end = 24.dp, bottom = inset,
                                    ),
                                )
                            }
                        },
                        colors = overlayColors,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = panelHeightDp + 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = stringResource(R.string.cd_recenter_map),
                        )
                    }
                }
            }
        }

        // ── Replay transport panel ────────────────────────────────────
        // A sibling to RouteMap inside the outer Column, so it sits below the
        // map rather than on top of it. Wrapped in AnimatedVisibility: the
        // enter/exit slide animates the panel's height, and because the map
        // above is weight(1f) it grows/shrinks in lockstep — one smooth motion
        // with no snap. tween (not spring) per the project's no-jitter rule.
        val rPanel = replay
        if (rPanel != null) {
            val onScrub: (Float) -> Unit = { f ->
                playing = false
                scope.launch { rPanel.progress.snapTo(f.coerceIn(0f, 1f)) }
            }
            AnimatedVisibility(
                visible = replaying,
                modifier = Modifier.align(Alignment.BottomCenter),
                // Slide in on open. The CLOSE is handled by panelDragY (the
                // drag handle animates it off-screen, then flips replaying
                // false), so the exit transition is None — otherwise it would
                // double up with, or jump against, the drag offset.
                enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it },
                exit = ExitTransition.None,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    // Shadow lifts the panel off the map so the seam reads even
                    // where the rounded corners sit over pale map tiles.
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(
                        topStart = panelCornerRadius,
                        topEnd = panelCornerRadius,
                    ),
                    modifier = Modifier
                        // Live drag offset (read in the layout phase so dragging
                        // re-positions without recomposing the panel contents).
                        .offset { IntOffset(0, panelDragY.value.roundToInt()) }
                        .fillMaxWidth()
                        .onSizeChanged { panelHeightPx = it.height },
                ) {
                    Column {
                        // Drag handle — the close affordance. Tap it or swipe it
                        // down to end the replay; that flips `replaying` false,
                        // and the AnimatedVisibility exit slides the whole panel
                        // (chart, stat line, buttons) out together. The hit area
                        // is the full-width row; the visible pill is centred.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    // Follow the finger live: each drag delta moves
                                    // panelDragY (clamped between fully open and the
                                    // panel height), so the panel sits wherever it's
                                    // held. On release, settle past ~30% → close,
                                    // else snap back open.
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (panelDragY.value > panelHeightPx * 0.3f) {
                                                closePanel()
                                            } else {
                                                scope.launch {
                                                    panelDragY.animateTo(
                                                        0f, tween(200, easing = FastOutSlowInEasing),
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                panelDragY.animateTo(
                                                    0f, tween(200, easing = FastOutSlowInEasing),
                                                )
                                            }
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        scope.launch {
                                            val h = panelHeightPx.toFloat().coerceAtLeast(1f)
                                            panelDragY.snapTo(
                                                (panelDragY.value + amount).coerceIn(0f, h),
                                            )
                                        }
                                    }
                                }
                                .clickable { closePanel() }
                                // A bit more padding than the pill needs, so the
                                // grab strip is comfortable without pushing the
                                // chart down and looking clunky.
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    ),
                            )
                        }
                    if (elevation != null) {
                        ElevationChart(
                            profile = elevation,
                            altitudeColor = altitudeColor,
                            speedColor = speedColor,
                            showGrid = showGrid,
                            progress = rPanel.progress.value,
                            onScrub = onScrub,
                            speedProfile = replaySpeedProfile,
                            showAltitude = showAltitude,
                            onShowAltitudeChange = onShowAltitudeChange,
                            showSpeed = showSpeed,
                            onShowSpeedChange = onShowSpeedChange,
                        )
                    } else {
                        Slider(
                            value = rPanel.progress.value,
                            onValueChange = onScrub,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    // Speed picker: preset row + a slider that snaps to the same
                    // presets, shown inline when tapped.
                    if (showSpeedPicker) {
                        val speedPresets = listOf(0.25f, 0.5f, 1f, 1.5f, 2f)
                        // SpaceBetween so the buttons sit at the even 0/¼/½/¾/1
                        // positions the slider's five stops use, and the active
                        // one is highlighted.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            speedPresets.forEach { preset ->
                                val selected = kotlin.math.abs(speed - preset) < 0.01f
                                TextButton(onClick = { speed = preset }) {
                                    Text(
                                        text = stringResource(R.string.replay_speed_format, formatSpeed(preset)),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        // The slider runs over an even 0..1 fraction with five
                        // stops; each stop maps to a preset, so dragging it can
                        // only land on a preset value (0.5× stays 0.5×, never an
                        // in-between like 0.75×) and the stops line up with the
                        // evenly-spaced buttons above.
                        val presetIndex = speedPresets
                            .indexOfFirst { kotlin.math.abs(it - speed) < 0.01f }
                            .coerceAtLeast(0)
                        Slider(
                            value = presetIndex / 4f,
                            onValueChange = { f ->
                                speed = speedPresets[(f * 4f).roundToInt().coerceIn(0, 4)]
                            },
                            valueRange = 0f..1f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    // Transport row: prev/play/next centred, speed at the end.
                    // (Exit lives in the drag handle above now.)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    playing = false
                                    scope.launch {
                                        rPanel.progress.snapTo(0f)
                                        playing = true
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_skip_previous),
                                    contentDescription = stringResource(R.string.cd_replay_restart),
                                )
                            }
                            FilledTonalIconButton(onClick = { playing = !playing }) {
                                Icon(
                                    painter = painterResource(
                                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                                    ),
                                    contentDescription = stringResource(
                                        if (playing) R.string.cd_replay_pause else R.string.cd_replay_play,
                                    ),
                                )
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    playing = false
                                    scope.launch { rPanel.progress.snapTo(1f) }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_skip_next),
                                    contentDescription = stringResource(R.string.cd_replay_to_end),
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { showSpeedPicker = !showSpeedPicker },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Text(
                                text = stringResource(R.string.replay_speed_format, formatSpeed(speed)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            }
        }
        } // closes outer Box
    }
}

// Explains the route-line gradient in a dialog: a horizontal gradient bar
// from dark to bright with "Start" / "End" labels, plus a one-sentence
// description. Shown when the user taps the info icon on the detail screen.
// Receives the resolved gradient colors from the caller so the preview
// always matches the actual route line on the map.
@Composable
private fun RouteLegendDialog(
    startColor: Color,
    endColor: Color,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.legend_ok))
            }
        },
        title = { Text(stringResource(R.string.legend_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.legend_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Visual gradient bar matching the route line's colours.
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(startColor, endColor),
                        ),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.legend_start),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.legend_end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // OSM ODbL requires attribution to be reasonably prominent.
                // The MapLibre attribution button is disabled (it was broken),
                // so we carry the credit here instead.
                Text(
                    text = stringResource(R.string.legend_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        },
    )
}

// The session's GPS track on a map. Builds a GeoJSON LineString from the
// points, draws it as a white-cased coloured line, and fits the camera to
// the track's bounding box so the whole drive is visible regardless of
// where it happened. `overlay` is a slot for caller-supplied controls
// (fullscreen button on the preview, close + recenter buttons on the
// fullscreen view) that get the live cameraState and the route's bounds
// so a recenter button can re-run the same animateTo as the initial fit.
@Composable
private fun RouteMap(
    points: List<GpsPoint>,
    styleUrl: String,
    startColor: Color,
    endColor: Color,
    modifier: Modifier = Modifier,
    gestures: GestureOptions = GestureOptions.Standard,
    onTap: (() -> Unit)? = null,
    // GL layers drawn on top of the route, inside the MaplibreMap content so
    // they live on the map's render surface (the replay dot uses this — a
    // CircleLayer here moves in lockstep with the tiles, unlike a Compose
    // overlay which the map would lag behind during pan/zoom).
    mapContent: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.(CameraState, BoundingBox?) -> Unit = { _, _ -> },
) {
    val cameraState = rememberCameraState()

    // GeoJSON is longitude-first, so Position(lng, lat). Recomputed only
    // when the point list changes identity. Nullable so the map shell can
    // render before the Room query for GPS points has returned anything —
    // a LineString needs at least 2 vertices, and below that there is
    // nothing to draw, so the route layers are simply skipped this frame.
    val routeLine = remember(points) {
        if (points.size >= 2) LineString(points.map { Position(it.lng, it.lat) })
        else null
    }

    // Tightest box containing the whole track. Same nullability as routeLine
    // so the camera-fit LaunchedEffect waits for real data instead of
    // calling minOf on an empty list.
    val bounds = remember(points) {
        if (points.size >= 2) BoundingBox(
            west = points.minOf { it.lng },
            south = points.minOf { it.lat },
            east = points.maxOf { it.lng },
            north = points.maxOf { it.lat },
        )
        else null
    }

    // animateTo suspends on awaitMap() until the map is attached, so firing
    // it on first composition is safe — it just waits. Padding keeps the
    // route off the very edges of the 240dp viewport. Skipped while bounds
    // is null; the map sits at MapLibre's default world view for that
    // single first frame, then flies to the route once points load.
    LaunchedEffect(bounds) {
        if (bounds != null) {
            cameraState.animateTo(bounds, padding = PaddingValues(32.dp))
        }
    }

    // Repaint-on-resume fix. With renderMode = TextureView, returning to the app
    // from the recents switcher leaves the map surface blank until something
    // forces a redraw — a touch currently does it, which is the reported bug
    // (buttons stay, tiles vanish, tiles return on tap). MapLibre's own
    // lifecycle observer calls MapView.onResume(), but the TextureView doesn't
    // emit a frame until the camera moves. So on each ON_RESUME we re-fit the
    // camera to the route bounds, which both forces a render (un-blanking the
    // tiles with no user interaction) and guarantees correct framing — unlike
    // nudging cameraState.position, which on resume reads back a stale default
    // (world view) before MapLibre has synced its real camera and would jump
    // the map all the way out. withFrameNanos yields one frame first so the
    // surface is reattached before we move the camera.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    LaunchedEffect(resumeTick) {
        if (resumeTick > 0 && bounds != null) {
            withFrameNanos {}
            cameraState.jumpTo(bounds, padding = PaddingValues(32.dp))
        }
    }

    // The MapLibre Android scale-bar plugin reads the device locale and shows
    // miles on US-locale phones — wrong for an app whose only unit is km.
    // Disable it (and the rest of the native ornaments). The Material3 ScaleBar
    // below replaces it with a guaranteed-metric draw, and the fullscreen
    // overlay draws its own compass to match the other map buttons.
    Box(modifier = modifier) {
    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        baseStyle = BaseStyle.Uri(styleUrl),
        cameraState = cameraState,
        options = MapOptions(
            // isScaleBarEnabled = false: replaced by Material3 ScaleBar (metric-forced).
            // isAttributionEnabled = false: broken by tap-interceptor; moved to dialog.
            // isLogoEnabled = false: MapLibre is BSD-3 — no UI logo required. OSM ODbL
            // attribution is the only legal obligation, handled in RouteLegendDialog.
            ornamentOptions = OrnamentOptions(
                isScaleBarEnabled = false,
                isAttributionEnabled = false,
                isLogoEnabled = false,
                // The native compass is a heavy opaque-black disc that clashes
                // with our translucent overlay buttons. Disable it; the
                // fullscreen overlay draws its own compass in the matching
                // style (see RouteMapFullscreen).
                isCompassEnabled = false,
            ),
            gestureOptions = gestures,
            // TextureView renders in the normal Compose layer instead of a
            // separate platform window. SurfaceView (the default) ignores
            // Compose's animation/alpha, so it ghosts briefly when navigating
            // away from the detail screen — TextureView eliminates the ghost.
            renderOptions = RenderOptions(
                renderMode = RenderOptions.RenderMode.TextureView,
            ),
        ),
    ) {
        // Route layers only attach once we actually have a line to draw, so
        // the map shell can paint tiles before the GPS query returns.
        if (routeLine != null) {
            // lineMetrics = true makes MapLibre compute a 0..1 distance-along-line
            // value for every vertex. Without it, feature.lineProgress() (used by
            // the gradient below) has nothing to interpolate against and the line
            // renders a flat colour.
            val routeSource = rememberGeoJsonSource(
                GeoJsonData.Features(routeLine),
                GeoJsonOptions(lineMetrics = true),
            )
            // A wider white line under the coloured one gives the route
            // contrast over any tile colour (roads, parks, water).
            LineLayer(
                id = "route-casing",
                source = routeSource,
                color = const(Color.White),
                width = const(7.dp),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
            LineLayer(
                id = "route",
                source = routeSource,
                // Dark (start) to bright (end) along the drive. lineProgress runs
                // 0 at the first recorded point to 1 at the last; points are stored
                // oldest-first, so 0 is genuinely where the car set off.
                gradient = interpolate(
                    linear(),
                    feature.lineProgress(),
                    0f to const(startColor),
                    1f to const(endColor),
                ),
                width = const(4.dp),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
        }
        // Caller-supplied GL layers (the replay dot) draw above the route line.
        mapContent()
    }
        // A transparent click-grabber sits above the MaplibreMap because the
        // map's underlying AndroidView consumes touches before Compose's
        // .clickable on the parent Box ever sees them. With gestures disabled
        // there's nothing the map needs the touches for. Skipped when no
        // onTap is supplied (fullscreen mode lets the map handle pan/zoom).
        if (onTap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onTap),
            )
        }
        // metersPerDpAtTarget is 0.0 before the map first emits a camera frame;
        // the ScaleBar composable early-returns on 0.0, so it just stays hidden
        // for the first frame and appears once the camera-fit animation runs.
        // Default colour is LocalContentColor — light in dark mode, which is
        // invisible on the light-themed map tiles. Force a dark line + text
        // with a white halo so it reads on any tile colour.
        ScaleBar(
            metersPerDp = cameraState.metersPerDpAtTarget,
            measures = ScaleBarMeasures(primary = ScaleBarMeasure.Metric),
            color = Color.Black,
            haloColor = Color.White,
            haloWidth = 2.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
        // Caller-supplied controls (expand / close / recenter). Drawn last so
        // they sit on top of the scale bar and the map's own ornaments.
        overlay(cameraState, bounds)
    }
}

// Parse an AARRGGBB hex string (e.g. "FF493F59") to a Compose Color.
// Used to convert the DataStore-persisted gradient preferences into
// typed Color values for the map gradient and legend preview.
private fun hexToComposeColor(hex: String): Color {
    val argb = android.graphics.Color.parseColor("#$hex")
    return Color(
        red = (argb shr 16) and 0xFF,
        green = (argb shr 8) and 0xFF,
        blue = argb and 0xFF,
        alpha = (argb ushr 24) and 0xFF,
    )
}

// Write the session's GPS track as a minimal valid GPX 1.1 file to
// cacheDir/exports/, then fire a share sheet via FileProvider so the
// user can send it to JOSM, Komoot, etc. No WRITE_EXTERNAL_STORAGE
// permission is needed — the cache dir is app-private, and FileProvider
// hands a read-only URI to the receiving app.
private suspend fun exportSessionAsGpx(
    context: Context,
    fileName: String,
    sessionDate: String,
    points: List<GpsPoint>,
) {
    val file = withContext(Dispatchers.IO) {
        val gpxContent = buildGpxString(sessionDate, points)
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        // App-private cache: a re-share of the same session reuses the name
        // and overwrites harmlessly, so no de-duplication is needed here.
        val f = File(dir, fileName)
        f.writeText(gpxContent)
        f
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        // clipData carries the URI grant to the share-sheet preview process,
        // which EXTRA_STREAM alone does not reach. Without it the chooser
        // can't read the file metadata to render a preview.
        clipData = android.content.ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

// Stream a session's GPX into a SAF document URI the user just picked in the
// system create-document dialog ("Save to device"). Returns true on success.
// Runs on IO because it opens a content-resolver output stream.
private suspend fun writeGpxToUri(
    context: Context,
    uri: Uri,
    sessionDate: String,
    points: List<GpsPoint>,
): Boolean = withContext(Dispatchers.IO) {
    val gpxContent = buildGpxString(sessionDate, points)
    try {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: return@withContext false
        stream.use { it.write(gpxContent.toByteArray()) }
        true
    } catch (e: java.io.IOException) {
        // The destination can become unwritable between picking it and
        // writing (removed SD card, revoked grant). Surface as a failed
        // toast rather than crashing the save flow.
        false
    }
}

// Quick save: write the GPX into the user's persisted default folder (a SAF
// tree URI) with no picker. baseName is the filename without extension; if a
// "<baseName>.gpx" already exists we append " (1)", " (2)", … so a re-save
// never silently clobbers the previous file. Returns the folder's display
// name on success so the caller can name it in a toast, or null on failure.
private suspend fun quickSaveGpx(
    context: Context,
    treeUriString: String,
    baseName: String,
    sessionDate: String,
    points: List<GpsPoint>,
): String? = withContext(Dispatchers.IO) {
    val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
        ?: return@withContext null
    if (!dir.canWrite()) return@withContext null
    val gpxContent = buildGpxString(sessionDate, points)
    try {
        val name = uniqueFileName(dir, baseName, "gpx")
        val file = dir.createFile("application/gpx+xml", name)
            ?: return@withContext null
        val stream = context.contentResolver.openOutputStream(file.uri)
            ?: return@withContext null
        stream.use { it.write(gpxContent.toByteArray()) }
        dir.name
    } catch (e: java.io.IOException) {
        // Persisted grant can be revoked if the user deletes/moves the folder
        // in their file manager; treat as a recoverable failure.
        null
    }
}

// Pick a display name not already present in the SAF folder, appending
// " (1)", " (2)", … before the extension until one is free. SAF's createFile
// auto-renames on conflict on some providers but not reliably, so we resolve
// it ourselves to guarantee the (n) behaviour the user expects. findFile is a
// linear scan, but export folders are small so the cost is negligible.
private fun uniqueFileName(dir: DocumentFile, baseName: String, extension: String): String {
    if (dir.findFile("$baseName.$extension") == null) return "$baseName.$extension"
    var n = 1
    while (dir.findFile("$baseName ($n).$extension") != null) n++
    return "$baseName ($n).$extension"
}

// Build the GPX 1.1 XML string for a session. Each GPS point becomes a
// <trkpt> element; altitude is included when it was recorded. Timestamp
// is the kotlinx Instant ISO-8601 string, which GPX 1.1 requires.
private fun buildGpxString(sessionDate: String, points: List<GpsPoint>): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine("""<gpx version="1.1" creator="Kilomètre" xmlns="http://www.topografix.com/GPX/1/1">""")
    appendLine("  <trk>")
    appendLine("    <name>$sessionDate</name>")
    appendLine("    <trkseg>")
    for (p in points) {
        val ele = if (p.altitudeMeters != null) "<ele>${p.altitudeMeters}</ele>" else ""
        appendLine(
            """      <trkpt lat="${p.lat}" lon="${p.lng}">${ele}<time>${p.timestamp}</time></trkpt>"""
        )
    }
    appendLine("    </trkseg>")
    appendLine("  </trk>")
    append("</gpx>")
}

// A titled group rendered as one rounded surfaceContainer card with a muted
// uppercase header floating above it. Mirrors SettingsScreen.SettingsGroup so
// the detail screen reads as part of the same app, not a different one. The
// content is a ColumnScope so callers stack rows (or the route map) directly.
@Composable
private fun StatGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(content = content)
        }
    }
}

// The elevation profile drawn as a filled area chart: a smoothed altitude line
// with a soft gradient fill beneath it, framed by the max altitude above and
// the min altitude/speed + route length below. Pure Compose Canvas — no chart
// library, no network. Altitude and speed are independent checkboxes: either
// or both can be shown simultaneously, overlaid in different colours (altitude
// uses the Material primary colour; speed uses a fixed warm amber so the two
// lines are always visually distinct). The checkbox row only appears when
// speed data is available.
@Composable
private fun ElevationChart(
    profile: ElevationProfile,
    // Altitude and speed curve colours (Settings → Chart), each a solid line,
    // and whether to draw the grid. Altitude defaults to a lavender, speed to
    // amber, so the two stay visually distinct.
    altitudeColor: Color = StatAccentPurple,
    speedColor: Color = StatAccentAmber,
    showGrid: Boolean = true,
    // When non-null (drive replay), draws a vertical cursor at this normalised
    // position along the route (0..1) with a dot on each active curve.
    // Null on the static detail-screen chart.
    progress: Float? = null,
    // When non-null, the chart becomes interactive: tapping or dragging on it
    // reports the touched x as a 0..1 fraction of the route, turning the cursor
    // dot into a scrubber. Null on the static detail-screen chart.
    onScrub: ((Float) -> Unit)? = null,
    speedProfile: SpeedProfile? = null,
    showAltitude: Boolean = true,
    onShowAltitudeChange: ((Boolean) -> Unit)? = null,
    showSpeed: Boolean = false,
    onShowSpeedChange: ((Boolean) -> Unit)? = null,
) {
    val altMinY = profile.minAltitude
    val altMaxY = profile.maxAltitude
    val altRange = (altMaxY - altMinY).coerceAtLeast(1f)
    val altTotalD = profile.totalDistanceMeters.coerceAtLeast(1f)
    val captionColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Faint grid colour drawn behind the curves.
    val gridColor = captionColor.copy(alpha = 0.12f)

    // Speed axis is anchored at a 0 km/h baseline (reads more naturally than the
    // drive's min speed); altitude keeps its real min so the climb fills the box.
    val speedMax = (speedProfile?.maxSpeed ?: 1f).coerceAtLeast(1f)

    // Tick labels for the five gridlines: value axes top→bottom, distance
    // left→right. Resolved here so the Canvas draw closure gets ready strings.
    val kmUnit = stringResource(R.string.today_km_unit)
    val altTicks = (0..4).map { i -> "%.0f".format(altMaxY - i / 4f * altRange) }
    val speedTicks = (0..4).map { i -> "%.0f".format(speedMax - i / 4f * speedMax) }
    val totalKm = altTotalD / 1000f
    val distTicks = (0..4).map { j ->
        val v = "%.0f".format(j / 4f * totalKm)
        if (j == 4) "$v $kmUnit" else v
    }

    // Plot gutters: reserved for the edge labels so the plotted area (grid +
    // curves + cursor) is a fixed rect regardless of which series is shown.
    val leftGutter = 30.dp
    val rightGutter = 30.dp
    val topPad = 6.dp
    val bottomGutter = 16.dp

    Column(modifier = Modifier.padding(16.dp)) {
        // Header: just the Alt/Speed checkboxes (the max/min values live on the
        // grid's edge labels now). Only shown when there's speed data to toggle.
        if (speedProfile != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = showAltitude,
                    onCheckedChange = onShowAltitudeChange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.session_chart_mode_altitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = altitudeColor,
                )
                Spacer(Modifier.width(16.dp))
                Checkbox(
                    checked = showSpeed,
                    onCheckedChange = onShowSpeedChange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.session_chart_mode_speed),
                    style = MaterialTheme.typography.labelSmall,
                    color = speedColor,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val currentScrub = rememberUpdatedState(onScrub)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .then(
                    if (onScrub != null) {
                        // Map taps/drags within the plot rect (between the
                        // gutters) to a 0..1 fraction of the route.
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val l = leftGutter.toPx()
                                    val pw = (size.width - l - rightGutter.toPx()).coerceAtLeast(1f)
                                    currentScrub.value?.invoke(((offset.x - l) / pw).coerceIn(0f, 1f))
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val l = leftGutter.toPx()
                                    val pw = (size.width - l - rightGutter.toPx()).coerceAtLeast(1f)
                                    currentScrub.value?.invoke(((change.position.x - l) / pw).coerceIn(0f, 1f))
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // Grid + edge labels, behind the curves. Static — no per-frame work.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val l = leftGutter.toPx()
                val r = size.width - rightGutter.toPx()
                val t = topPad.toPx()
                val b = size.height - bottomGutter.toPx()
                val pw = r - l
                val ph = b - t
                // Gridlines (toggle via Settings → Chart); the edge labels stay
                // either way so the axes are still readable without the mesh.
                if (showGrid) {
                    for (i in 0..4) {
                        val y = t + i / 4f * ph
                        drawLine(gridColor, Offset(l, y), Offset(r, y), 1.dp.toPx())
                    }
                    for (j in 0..4) {
                        val x = l + j / 4f * pw
                        drawLine(gridColor, Offset(x, t), Offset(x, b), 1.dp.toPx())
                    }
                }
                val nc = drawContext.canvas.nativeCanvas
                val tsPx = 9.5.sp.toPx()
                val pad = 4.dp.toPx()
                fun paint(argb: Int, align: android.graphics.Paint.Align) =
                    android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = tsPx
                        color = argb
                        textAlign = align
                    }
                // Left edge: altitude (m). Right edge: speed (km/h). Each only
                // when its series is shown.
                if (showAltitude) {
                    val p = paint(altitudeColor.toArgb(), android.graphics.Paint.Align.RIGHT)
                    altTicks.forEachIndexed { i, s ->
                        nc.drawText(s, l - pad, t + i / 4f * ph + tsPx * 0.35f, p)
                    }
                }
                if (showSpeed && speedProfile != null) {
                    val p = paint(speedColor.toArgb(), android.graphics.Paint.Align.LEFT)
                    speedTicks.forEachIndexed { i, s ->
                        nc.drawText(s, r + pad, t + i / 4f * ph + tsPx * 0.35f, p)
                    }
                }
                // Bottom edge: distance (km), always shown.
                val capArgb = captionColor.toArgb()
                val dy = b + tsPx + 2.dp.toPx()
                distTicks.forEachIndexed { j, s ->
                    val x = l + j / 4f * pw
                    val align = when (j) {
                        0 -> android.graphics.Paint.Align.LEFT
                        4 -> android.graphics.Paint.Align.RIGHT
                        else -> android.graphics.Paint.Align.CENTER
                    }
                    nc.drawText(s, x, dy, paint(capArgb, align))
                }
            }
            // Curves + cursor, inset to the plot rect via matching padding so
            // their own full-size mapping lands exactly on the grid.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = leftGutter, top = topPad, end = rightGutter, bottom = bottomGutter),
            ) {
                if (showAltitude) {
                    ElevationCurve(profile.samples, altitudeColor, altitudeColor, altMinY, altRange, altTotalD)
                }
                if (showSpeed && speedProfile != null) {
                    val sTotalD = speedProfile.totalDistanceMeters.coerceAtLeast(1f)
                    // Amber, anchored at the 0 km/h baseline to match the axis.
                    ElevationCurve(speedProfile.samples, speedColor, speedColor, 0f, speedMax, sTotalD)
                }
                if (progress != null) {
                    if (showAltitude) {
                        ElevationCursor(profile.samples, progress, altitudeColor, altitudeColor, altMinY, altRange, altTotalD)
                    }
                    if (showSpeed && speedProfile != null) {
                        val sTotalD = speedProfile.totalDistanceMeters.coerceAtLeast(1f)
                        ElevationCursor(speedProfile.samples, progress, speedColor, speedColor, 0f, speedMax, sTotalD)
                    }
                }
            }
        }
    }
}

// The static part of the chart: the filled area + the smoothed line.
// Pulled out as its own composable taking only stable inputs so a replay's
// per-frame `progress` change skips it entirely (it recomposes only if the
// samples or colour actually changes), leaving the heavy path build off the
// animation hot path. `samples` is a list of (distanceMeters, value) pairs;
// `minY` and `range` define the y-axis scale; `totalD` is the x-axis extent.
// startColor/endColor are used for a horizontal gradient matching the route line;
// passing the same colour twice gives a solid line (used for the speed curve).
@Composable
private fun ElevationCurve(
    samples: List<Pair<Float, Float>>,
    startColor: Color,
    endColor: Color = startColor,
    minY: Float,
    range: Float,
    totalD: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val line = Path()
        val fill = Path()
        samples.forEachIndexed { i, (d, a) ->
            val x = d / totalD * w
            val y = h - (a - minY) / range * h
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(samples.last().first / totalD * w, h)
        fill.close()
        // Fill: vertical alpha ramp from the start colour so the area beneath
        // the line is tinted without being heavy.
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(startColor.copy(alpha = 0.30f), Color.Transparent),
            ),
        )
        // Line: horizontal gradient from start to end colour, mirroring the
        // route gradient on the map. For a solid series (speed, same colour
        // both ends) this degrades to a plain stroke.
        drawPath(
            path = line,
            brush = Brush.horizontalGradient(listOf(startColor, endColor)),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

// The replay cursor overlay: a vertical guide plus a dot sitting on the curve
// at the current position. `progress` is a fraction of route distance and the
// x-axis is route distance, so cx = progress * width directly; the value at
// that x is interpolated from the bracketing samples. This is the only layer
// that repaints per frame, and it's just a line and two circles.
// The cursor colour is interpolated between startColor and endColor at the
// current progress so the dot tracks the same gradient as the route line.
@Composable
private fun ElevationCursor(
    samples: List<Pair<Float, Float>>,
    progress: Float,
    startColor: Color,
    endColor: Color = startColor,
    minY: Float,
    range: Float,
    totalD: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val f = progress.coerceIn(0f, 1f)
        val cx = f * w
        val targetD = f * totalD
        val s = samples
        var lo = 0
        var hi = s.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (s[mid].first <= targetD) lo = mid else hi = mid - 1
        }
        val valF = if (lo >= s.size - 1) {
            s.last().second
        } else {
            val (d0, a0) = s[lo]
            val (d1, a1) = s[lo + 1]
            val t = if (d1 > d0) (targetD - d0) / (d1 - d0) else 0f
            a0 + (a1 - a0) * t
        }
        val cy = h - (valF - minY) / range * h
        // Interpolate cursor colour along the gradient so the dot visually
        // shows where in the start→end ramp the replay head currently sits.
        val cursorColor = Color(
            red = startColor.red + (endColor.red - startColor.red) * f,
            green = startColor.green + (endColor.green - startColor.green) * f,
            blue = startColor.blue + (endColor.blue - startColor.blue) * f,
            alpha = 1f,
        )
        drawLine(
            color = cursorColor.copy(alpha = 0.5f),
            start = Offset(cx, 0f),
            end = Offset(cx, h),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = cursorColor, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
    }
}

// One stat line inside a StatGroup: leading icon badge, the stat label, and
// the value pushed to the right in a medium weight. The same badge + spacing
// rhythm as a SettingsRow, so the two screens share a look.
@Composable
private fun StatItemRow(icon: ImageVector, accent: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatIconBadge(icon, accent)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// A faint hairline between stat rows, inset from the left so it doesn't touch
// the card edge. Matches SettingsScreen.RowDivider.
@Composable
private fun StatRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// The round tinted badge leading each stat row: the icon in full accent over
// a disc of the same hue at 18% alpha. Identical treatment to the Settings
// screen's IconBadge so the two never visually diverge.
@Composable
private fun StatIconBadge(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

// Formats a playback-rate multiplier for the speed control: drops a trailing
// ".0" so 1f reads "1" not "1.0", keeps one or two decimals otherwise (1.5,
// 0.25). Rounded to two decimals first so the free slider never shows float
// noise like "1.3700001".
private fun formatSpeed(s: Float): String {
    val rounded = (s * 100f).roundToInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        rounded.toInt().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}

// Accent colours for stat badges and chart curves. Deliberately fixed (not
// theme colorScheme roles) so they stay constant across light/dark.
// Three-colour semantic scheme: Blue = motion (distance, speed), Purple = time
// (duration, clock, paused), Amber = speed curve on the elevation chart.
private val StatAccentBlue = Color(0xFF4C8DF5)
private val StatAccentPurple = Color(0xFF9B7BE8)
private val StatAccentSlate = Color(0xFF8290A4)   // reserved for future neutral rows
private val StatAccentAmber = Color(0xFFE0A02E)
