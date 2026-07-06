package dev.melcodes.kilometre.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import kotlinx.coroutines.launch

// Profile settings category: the driver's name, their accompagnateurs (opens a
// dedicated management screen), and the AAC kilometre goal. Reached from the
// Settings hub. Reads the Driver + accompagnateur list from Room-backed Flows
// so subtitles refresh after any edit.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onBack: () -> Unit,
    onNavigateToAccompagnateurs: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()

    val driver by container.driver.collectAsStateWithLifecycle(initialValue = null)
    val accompagnateurs by container.accompagnateurs.collectAsStateWithLifecycle(initialValue = emptyList())
    val aacModeEnabled by container.aacModeEnabled.collectAsStateWithLifecycle(initialValue = false)

    var showEditName by remember { mutableStateOf(false) }
    var showEditGoal by remember { mutableStateOf(false) }

    val groupIcon = ImageVector.vectorResource(R.drawable.ic_group)
    val flagIcon = ImageVector.vectorResource(R.drawable.ic_flag)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.settings_section_profile)) },
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
            item {
                SettingsGroup(stringResource(R.string.settings_section_profile)) {
                    SettingsRow(
                        icon = Icons.Filled.Person,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_your_name),
                        subtitle = driver?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_value_not_set),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = { showEditName = true },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = groupIcon,
                        accent = AccentTeal,
                        title = stringResource(R.string.settings_accompagnateurs),
                        subtitle = stringResource(R.string.settings_accompagnateurs_count, accompagnateurs.size),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onNavigateToAccompagnateurs,
                    )
                    RowDivider()
                    // AAC mode toggle: when on, the goal cycles through the
                    // milestones automatically (1000 → 3000) and the RDV
                    // reminders fire, so the manual goal-edit row below is
                    // hidden. When off, the goal is a static value the user sets.
                    SettingsRow(
                        icon = flagIcon,
                        accent = AccentGreen,
                        title = stringResource(R.string.settings_aac_mode),
                        subtitle = stringResource(R.string.settings_aac_mode_sub),
                        onClick = {
                            scope.launch {
                                if (aacModeEnabled) container.disableAacMode()
                                else container.enableAacMode()
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = aacModeEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        if (it) container.enableAacMode()
                                        else container.disableAacMode()
                                    }
                                },
                            )
                        },
                    )
                    if (!aacModeEnabled) {
                        RowDivider()
                        SettingsRow(
                            icon = flagIcon,
                            accent = AccentAmber,
                            title = stringResource(R.string.settings_km_goal),
                            subtitle = stringResource(R.string.settings_km_goal_value, driver?.kmGoal ?: 3000),
                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            onClick = { showEditGoal = true },
                        )
                    }
                }
            }
        }
    }

    if (showEditName) {
        EditTextDialog(
            title = stringResource(R.string.settings_edit_name_title),
            initial = driver?.name ?: "",
            singleLine = true,
            keyboardType = KeyboardType.Text,
            onSave = { name ->
                scope.launch { container.updateDriverName(name) }
                showEditName = false
            },
            onDismiss = { showEditName = false },
        )
    }

    if (showEditGoal) {
        EditTextDialog(
            title = stringResource(R.string.settings_edit_goal_title),
            initial = (driver?.kmGoal ?: 3000).toString(),
            singleLine = true,
            keyboardType = KeyboardType.Number,
            onSave = { text ->
                text.toIntOrNull()?.takeIf { it > 0 }?.let { goal ->
                    scope.launch { container.updateDriverKmGoal(goal) }
                }
                showEditGoal = false
            },
            onDismiss = { showEditGoal = false },
        )
    }
}
