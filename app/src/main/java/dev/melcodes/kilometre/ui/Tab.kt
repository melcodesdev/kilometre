package dev.melcodes.kilometre.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import dev.melcodes.kilometre.R

// The three bottom-nav destinations. Adding a fourth (Settings, in
// Phase 5) means a new enum entry and a matching `composable(...)`
// in the NavHost — that's the entire wiring contract.
//
// The icons here are placeholders from `material-icons-core`. The
// "real" choices (e.g. a list icon for Sessions, a trending-up icon
// for Progress) live in `material-icons-extended`, which we add when
// the rest of the UI catches up. Star and Menu work as stand-ins.
enum class Tab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY("today", R.string.nav_today, Icons.Default.LocationOn),
    SESSIONS("sessions", R.string.nav_sessions, Icons.Default.Menu),
    PROGRESS("progress", R.string.nav_progress, Icons.Default.Star),
}
