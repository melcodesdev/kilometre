package dev.melcodes.kilometre.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.DEFAULT_CHART_ALTITUDE_HEX
import dev.melcodes.kilometre.DEFAULT_CHART_SPEED_HEX
import dev.melcodes.kilometre.DEFAULT_GRADIENT_END_HEX
import dev.melcodes.kilometre.DEFAULT_GRADIENT_START_HEX
import dev.melcodes.kilometre.DEFAULT_REPLAY_DOT_HEX
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import kotlinx.coroutines.launch

// Map & replay settings category: everything that configures how a recorded
// drive is shown on the session-detail screen — the map tile style and route
// gradient (Map), the replay transport behaviour (Replay), and the elevation/
// speed chart (Chart). These were three separate top-level sections before the
// hub split; they live together here because they all tune the same viewing
// experience. Reached from the Settings hub.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapReplaySettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()

    val mapStyle by container.mapStyle.collectAsStateWithLifecycle(initialValue = "liberty")
    val startHex by container.gradientStartHex.collectAsStateWithLifecycle(initialValue = DEFAULT_GRADIENT_START_HEX)
    val endHex by container.gradientEndHex.collectAsStateWithLifecycle(initialValue = DEFAULT_GRADIENT_END_HEX)
    val replayDuration by container.replayDurationSeconds.collectAsStateWithLifecycle(initialValue = 20)
    val replayScaleToDistance by container.replayScaleToDistance.collectAsStateWithLifecycle(initialValue = false)
    val replayFollow by container.replayFollowDefault.collectAsStateWithLifecycle(initialValue = false)
    val replayDefaultSpeed by container.replayDefaultSpeed.collectAsStateWithLifecycle(initialValue = 1f)
    val replayLoop by container.replayLoop.collectAsStateWithLifecycle(initialValue = false)
    val altitudeSmoothing by container.altitudeSmoothingHalf.collectAsStateWithLifecycle(initialValue = 15)
    val speedSmoothing by container.speedSmoothingHalf.collectAsStateWithLifecycle(initialValue = 7)
    val chartShowGrid by container.chartShowGrid.collectAsStateWithLifecycle(initialValue = true)
    val chartSpeedHex by container.chartSpeedHex.collectAsStateWithLifecycle(initialValue = DEFAULT_CHART_SPEED_HEX)
    val replayDotHex by container.replayDotHex.collectAsStateWithLifecycle(initialValue = DEFAULT_REPLAY_DOT_HEX)
    val chartAltitudeHex by container.chartAltitudeHex.collectAsStateWithLifecycle(initialValue = DEFAULT_CHART_ALTITUDE_HEX)

    // null = no sheet; "start"/"end"/"speed"/"altitude"/"dot" pick which colour.
    var colorPickerSlot by remember { mutableStateOf<String?>(null) }
    // (title, body) of the info-explanation dialog opened by a row's ⓘ button.
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Pre-resolved info texts (stringResource can't be called from the ⓘ lambda).
    val paceInfo = stringResource(R.string.settings_replay_pace) to
        stringResource(R.string.settings_replay_pace_hint)
    val scaleInfo = stringResource(R.string.settings_replay_scale) to
        stringResource(R.string.settings_replay_scale_hint)
    val altitudeSmoothingInfo = stringResource(R.string.settings_altitude_smoothing) to
        stringResource(R.string.settings_smoothing_hint)
    val speedSmoothingInfo = stringResource(R.string.settings_speed_smoothing) to
        stringResource(R.string.settings_smoothing_hint)

    val layersIcon = ImageVector.vectorResource(R.drawable.ic_layers)
    val gradientIcon = ImageVector.vectorResource(R.drawable.ic_gradient)
    val timerIcon = ImageVector.vectorResource(R.drawable.ic_timer)
    val speedIcon = ImageVector.vectorResource(R.drawable.ic_speed)
    val followIcon = ImageVector.vectorResource(R.drawable.ic_my_location)
    val paletteIcon = ImageVector.vectorResource(R.drawable.ic_palette)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.settings_section_mapreplay)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            // ── Map ────────────────────────────────────────────────────────
            item {
                SettingsGroup(stringResource(R.string.settings_section_map)) {
                    MapStyleRow(
                        icon = layersIcon,
                        accent = AccentGreen,
                        current = mapStyle,
                        onSet = { scope.launch { container.setMapStyle(it) } },
                    )
                    RowDivider()
                    GradientRow(
                        icon = gradientIcon,
                        accent = AccentPink,
                        startHex = startHex,
                        endHex = endHex,
                        onTapStart = { colorPickerSlot = "start" },
                        onTapEnd = { colorPickerSlot = "end" },
                    )
                }
            }

            // ── Replay ─────────────────────────────────────────────────────
            item {
                SettingsGroup(stringResource(R.string.settings_section_replay)) {
                    SegmentedSettingRow(
                        icon = timerIcon,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_replay_pace),
                        options = listOf(
                            10 to stringResource(R.string.settings_replay_pace_short),
                            20 to stringResource(R.string.settings_replay_pace_normal),
                            40 to stringResource(R.string.settings_replay_pace_long),
                        ),
                        current = replayDuration,
                        onSet = { scope.launch { container.setReplayDurationSeconds(it) } },
                        enabled = !replayScaleToDistance,
                        onInfo = { infoDialog = paceInfo },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = timerIcon,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_replay_scale),
                        onInfo = { infoDialog = scaleInfo },
                        onClick = { scope.launch { container.setReplayScaleToDistance(!replayScaleToDistance) } },
                        trailingContent = {
                            Switch(
                                checked = replayScaleToDistance,
                                onCheckedChange = { scope.launch { container.setReplayScaleToDistance(it) } },
                            )
                        },
                    )
                    RowDivider()
                    SegmentedSettingRow(
                        icon = speedIcon,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_replay_default_speed),
                        options = listOf(
                            0.25f to "0.25×", 0.5f to "0.5×", 1f to "1×", 1.5f to "1.5×", 2f to "2×",
                        ),
                        current = replayDefaultSpeed,
                        onSet = { scope.launch { container.setReplayDefaultSpeed(it) } },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = followIcon,
                        accent = AccentPurple,
                        title = stringResource(R.string.settings_replay_follow),
                        subtitle = stringResource(R.string.settings_replay_follow_hint),
                        onClick = { scope.launch { container.setReplayFollowDefault(!replayFollow) } },
                        trailingContent = {
                            Switch(
                                checked = replayFollow,
                                onCheckedChange = { scope.launch { container.setReplayFollowDefault(it) } },
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Refresh,
                        accent = AccentPurple,
                        title = stringResource(R.string.settings_replay_loop),
                        subtitle = stringResource(R.string.settings_replay_loop_hint),
                        onClick = { scope.launch { container.setReplayLoop(!replayLoop) } },
                        trailingContent = {
                            Switch(
                                checked = replayLoop,
                                onCheckedChange = { scope.launch { container.setReplayLoop(it) } },
                            )
                        },
                    )
                }
            }

            // ── Chart ──────────────────────────────────────────────────────
            item {
                SettingsGroup(stringResource(R.string.settings_section_chart)) {
                    SegmentedSettingRow(
                        icon = gradientIcon,
                        accent = AccentGreen,
                        title = stringResource(R.string.settings_altitude_smoothing),
                        options = listOf(
                            7 to stringResource(R.string.settings_smoothing_low),
                            15 to stringResource(R.string.settings_smoothing_medium),
                            31 to stringResource(R.string.settings_smoothing_high),
                        ),
                        current = altitudeSmoothing,
                        onSet = { scope.launch { container.setAltitudeSmoothingHalf(it) } },
                        onInfo = { infoDialog = altitudeSmoothingInfo },
                    )
                    RowDivider()
                    SegmentedSettingRow(
                        icon = speedIcon,
                        accent = AccentGreen,
                        title = stringResource(R.string.settings_speed_smoothing),
                        options = listOf(
                            3 to stringResource(R.string.settings_smoothing_low),
                            7 to stringResource(R.string.settings_smoothing_medium),
                            15 to stringResource(R.string.settings_smoothing_high),
                        ),
                        current = speedSmoothing,
                        onSet = { scope.launch { container.setSpeedSmoothingHalf(it) } },
                        onInfo = { infoDialog = speedSmoothingInfo },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = layersIcon,
                        accent = AccentTeal,
                        title = stringResource(R.string.settings_chart_grid),
                        subtitle = stringResource(R.string.settings_chart_grid_hint),
                        onClick = { scope.launch { container.setChartShowGrid(!chartShowGrid) } },
                        trailingContent = {
                            Switch(
                                checked = chartShowGrid,
                                onCheckedChange = { scope.launch { container.setChartShowGrid(it) } },
                            )
                        },
                    )
                    RowDivider()
                    ColorSettingRow(
                        icon = paletteIcon,
                        title = stringResource(R.string.settings_chart_altitude_color),
                        hex = chartAltitudeHex,
                        onClick = { colorPickerSlot = "altitude" },
                    )
                    RowDivider()
                    ColorSettingRow(
                        icon = paletteIcon,
                        title = stringResource(R.string.settings_chart_speed_color),
                        hex = chartSpeedHex,
                        onClick = { colorPickerSlot = "speed" },
                    )
                    RowDivider()
                    ColorSettingRow(
                        icon = paletteIcon,
                        title = stringResource(R.string.settings_replay_dot_color),
                        hex = replayDotHex,
                        onClick = { colorPickerSlot = "dot" },
                    )
                }
            }
        }
    }

    // ── Dialogs and sheets ───────────────────────────────────────────────
    val info = infoDialog
    if (info != null) {
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text(stringResource(R.string.legend_ok))
                }
            },
            title = { Text(info.first) },
            text = { Text(info.second) },
        )
    }

    val slot = colorPickerSlot
    if (slot != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val currentHex = when (slot) {
            "start" -> startHex
            "end" -> endHex
            "speed" -> chartSpeedHex
            "altitude" -> chartAltitudeHex
            else -> replayDotHex
        }
        val title = stringResource(
            when (slot) {
                "start" -> R.string.settings_color_picker_start
                "end" -> R.string.settings_color_picker_end
                "speed" -> R.string.settings_chart_speed_color
                "altitude" -> R.string.settings_chart_altitude_color
                else -> R.string.settings_replay_dot_color
            },
        )
        ColorPickerSheet(
            title = title,
            currentHex = currentHex,
            sheetState = sheetState,
            onSelect = { hex ->
                scope.launch {
                    when (slot) {
                        "start" -> container.setGradientStartHex(hex)
                        "end" -> container.setGradientEndHex(hex)
                        "speed" -> container.setChartSpeedHex(hex)
                        "altitude" -> container.setChartAltitudeHex(hex)
                        else -> container.setReplayDotHex(hex)
                    }
                }
                colorPickerSlot = null
            },
            onDismiss = { colorPickerSlot = null },
        )
    }
}
