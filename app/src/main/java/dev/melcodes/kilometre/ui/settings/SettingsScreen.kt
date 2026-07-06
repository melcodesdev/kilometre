package dev.melcodes.kilometre.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.R

// The Settings hub. A short, scannable list of categories — Profile, App,
// Map & replay, About — each opening its own screen with that category's
// settings (the system-Settings pattern). This file used to hold all ~7
// sections and every row inline (1200+ lines); the rows now live on the
// per-category screens, the shared widgets in SettingsComponents.kt.
//
// The hub has no Scaffold/TopAppBar of its own: AppShell owns the top bar for
// the "settings" route, with the gear icon doubling as the close affordance.
@Composable
fun SettingsScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToApp: () -> Unit,
    onNavigateToMapReplay: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val layersIcon = ImageVector.vectorResource(R.drawable.ic_layers)
    val paletteIcon = ImageVector.vectorResource(R.drawable.ic_palette)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item {
            // One rounded card holding the category rows (same tonal styling as
            // SettingsGroup, but without an uppercase header — the hub needs no
            // section label).
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.Person,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_section_profile),
                        subtitle = stringResource(R.string.settings_cat_profile_sub),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onNavigateToProfile,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = paletteIcon,
                        accent = AccentPurple,
                        title = stringResource(R.string.settings_section_app),
                        subtitle = stringResource(R.string.settings_cat_app_sub),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onNavigateToApp,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = layersIcon,
                        accent = AccentGreen,
                        title = stringResource(R.string.settings_section_mapreplay),
                        subtitle = stringResource(R.string.settings_cat_mapreplay_sub),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onNavigateToMapReplay,
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        accent = AccentSlate,
                        title = stringResource(R.string.settings_section_about),
                        subtitle = stringResource(R.string.settings_cat_about_sub),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onNavigateToAbout,
                    )
                }
            }
        }
    }
}
