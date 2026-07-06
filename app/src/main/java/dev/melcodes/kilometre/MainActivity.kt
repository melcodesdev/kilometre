package dev.melcodes.kilometre

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.melcodes.kilometre.ui.Tab
import dev.melcodes.kilometre.ui.onboarding.OnboardingScreen
import dev.melcodes.kilometre.ui.progress.ProgressScreen
import dev.melcodes.kilometre.ui.sessions.SessionDetailScreen
import dev.melcodes.kilometre.ui.sessions.SessionsScreen
import dev.melcodes.kilometre.ui.settings.AboutSettingsScreen
import dev.melcodes.kilometre.ui.settings.AccompagnateurManagementScreen
import dev.melcodes.kilometre.ui.settings.AppSettingsScreen
import dev.melcodes.kilometre.ui.settings.MapReplaySettingsScreen
import dev.melcodes.kilometre.ui.settings.ProfileSettingsScreen
import dev.melcodes.kilometre.ui.settings.SettingsScreen
import dev.melcodes.kilometre.ui.theme.KilometreTheme
import dev.melcodes.kilometre.ui.today.TodayScreen

// Single-activity host. Routes between onboarding and the tab shell based
// on the onboarding-complete flag. AppCompatActivity (not ComponentActivity)
// so the per-app language set via AppCompatDelegate.setApplicationLocales
// applies on every API level including 30-32.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opt into the panel's highest refresh rate. Android defaults apps
        // to 60 Hz even on 90/120 Hz screens unless the Window explicitly
        // requests a Display.Mode with a higher rate — without this Compose
        // animations cap out at 16.67 ms per frame.
        requestHighestRefreshRate()
        val container = (application as KilometreApp).container
        setContent {
            val themePreference by container.appTheme
                .collectAsStateWithLifecycle(initialValue = "system")
            KilometreTheme(themePreference = themePreference) {
                val onboarded by container.onboardingComplete
                    .collectAsStateWithLifecycle(initialValue = null)
                when (onboarded) {
                    null -> Surface(modifier = Modifier.fillMaxSize()) {}
                    false -> OnboardingScreen()
                    true -> AppShell()
                }
            }
        }
    }

    // Asks the platform for the highest refresh rate the panel supports. We
    // set both:
    //   preferredDisplayModeId: pin the Display.Mode (resolution + rate) with
    //     the highest rate at the current resolution.
    //   preferredRefreshRate: a separate hint that survives even when the
    //     compositor is choosing modes adaptively (Samsung "Adaptive" motion
    //     smoothness etc.) — without it, devices commonly ramp down to 60 Hz
    //     between gestures and animations appear to start at 60 fps before
    //     the panel catches up.
    private fun requestHighestRefreshRate() {
        val display = display ?: return
        val current = display.mode
        val sameRes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight
        }
        val best = sameRes.maxByOrNull { it.refreshRate } ?: return
        val highestRate = sameRes.maxOf { it.refreshRate }
        window.attributes = window.attributes.apply {
            if (best.modeId != current.modeId) preferredDisplayModeId = best.modeId
            preferredRefreshRate = highestRate
        }
    }
}

// Returns the Tab index for a route, or -1 for non-tab routes. Used by
// the NavHost transition lambdas to decide animation direction.
private fun tabIndex(route: String?): Int =
    Tab.entries.indexOfFirst { it.route == route }

// Settings is the only route we treat as an "app launch" — opening it
// scales up from 0.85, closing it scales back down. Session detail and
// the accompagnateurs sub-route keep the slide-from-right feel because
// they're depth navigations *within* a section, not a top-level overlay.
private fun isSettings(route: String?): Boolean = route == "settings"

// Spring used by the Settings open/close scale (app-launch feel). Small
// scale changes read smoothly under a spring; large translations don't,
// so tab slides use a tween instead (see TAB_SLIDE_MS).
private val NavSpring = Spring.StiffnessMediumLow

// Tab-to-tab page slide. A coordinated full-width translation where both
// screens move together in lockstep, like a ViewPager. A tween (not a
// spring) is deliberate: a critically-damped spring has a slow asymptotic
// tail that reads as the page "creeping" into place — laggy on a long
// slide — whereas FastOutSlowIn comes to a clean, definite stop. Both the
// entering and leaving screens MUST share this exact spec or they drift
// apart mid-slide.
private const val TAB_SLIDE_MS = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isMainTab = Tab.entries.any { it.route == currentRoute }
    val isSettings = currentRoute == "settings"

    // Track the last active tab so the bottom bar keeps it highlighted
    // when the user pushes into Settings or a session detail screen.
    // Without this, all three tabs appear unselected on non-tab routes,
    // which looks broken and makes it unclear how to navigate back.
    var lastActiveTab by remember { mutableStateOf(Tab.TODAY) }
    LaunchedEffect(currentRoute) {
        Tab.entries.find { it.route == currentRoute }?.let { lastActiveTab = it }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Top bar shows on the three main tabs and on Settings. On
            // Settings the gear icon stays in reach and acts as a toggle —
            // tap to close, returning to the tab the user came from. Deeper
            // routes (session detail, accompagnateurs management) have
            // their own top bars and skip this one.
            if (isMainTab || isSettings) {
                TopAppBar(
                    title = {
                        if (isSettings) Text(stringResource(R.string.settings_title))
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isSettings) {
                                navController.popBackStack()
                            } else {
                                navController.navigate("settings")
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    // On a main tab, highlight the current one. On a non-tab
                    // route (Settings, session detail), highlight the tab the
                    // user came from so they can tap it to go back.
                    val selected = if (isMainTab) {
                        currentRoute == tab.route
                    } else {
                        tab == lastActiveTab
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            when {
                                // Already on this tab: nothing to do.
                                currentRoute == tab.route -> {}
                                // On an overlay (Settings / session detail) and
                                // tapping the tab we came from: popBackStack so
                                // the overlay's close animation plays.
                                !isMainTab && tab == lastActiveTab ->
                                    navController.popBackStack()
                                else -> {
                                    // Switching tabs. If an overlay is open,
                                    // dismiss it WITHOUT saving its state first.
                                    // The saveState/restoreState pattern below
                                    // assumes every target is a sibling tab; if
                                    // the overlay is left on the back stack it
                                    // gets swept into the saved-state map and
                                    // later restored, which made Settings
                                    // reappear when bouncing between tabs.
                                    if (!isMainTab) {
                                        navController.popBackStack(
                                            lastActiveTab.route,
                                            inclusive = false,
                                        )
                                    }
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes),
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(innerPadding),
            // Three animation families:
            //   tab → tab: directional horizontal slide via a tween (see
            //     TAB_SLIDE_MS) so both screens stay locked together and
            //     stop cleanly. Going to a tab on
            //     the right (higher index) slides the new content in from
            //     the right; going left slides in from the left. The bar
            //     order (Today / Sessions / Progress) becomes a spatial
            //     map, which is easier to track than a crossfade.
            //   any → settings: app-launch — scales up from 0.7 + fade.
            //     The previous tab gets a slight outward zoom (1.05) for
            //     depth, like opening an app from a launcher icon.
            //   tab → session/accompagnateurs sub-route: slide in from
            //     the right (depth navigation within a section).
            enterTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                when {
                    // App-launch scale only when opening the hub FROM a tab.
                    // Hub -> category falls through to the depth slide below.
                    isSettings(targetState.destination.route) && from >= 0 ->
                        fadeIn(
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        ) + scaleIn(
                            initialScale = 0.7f,
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        )
                    from >= 0 && to >= 0 ->
                        slideInHorizontally(
                            initialOffsetX = { if (to > from) it else -it },
                            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing),
                        )
                    else -> fadeIn(tween(220, easing = LinearOutSlowInEasing)) +
                        slideInHorizontally(
                            initialOffsetX = { it / 3 },
                            animationSpec = tween(220, easing = LinearOutSlowInEasing),
                        )
                }
            },
            exitTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                when {
                    // App-launch: previous tab fades + slight zoom (depth).
                    // Only when a tab is the one being left for the hub.
                    isSettings(targetState.destination.route) && from >= 0 ->
                        fadeOut(
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        ) + scaleOut(
                            targetScale = 1.05f,
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        )
                    // App-close: Settings scales down + fades out while a
                    // tab fades in (when user taps a tab while in Settings).
                    // Only when leaving the hub FOR a tab; hub -> category
                    // (target not a tab) takes the depth slide below.
                    isSettings(initialState.destination.route) && to >= 0 ->
                        fadeOut(
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        ) + scaleOut(
                            targetScale = 0.7f,
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        )
                    // Tab → tab: the leaving screen slides fully off in the
                    // same direction, locked to the entering screen's spec.
                    from >= 0 && to >= 0 ->
                        slideOutHorizontally(
                            targetOffsetX = { if (to > from) -it else it },
                            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing),
                        )
                    // Tab → sub-route: slide out to mirror the slide-in.
                    else ->
                        fadeOut(tween(220, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(
                                targetOffsetX = { it / 3 },
                                animationSpec = tween(220, easing = FastOutLinearInEasing),
                            )
                }
            },
            // Pop transitions fire for popBackStack() AND for navigate()
            // calls that restore saved state — tab clicks use restoreState
            // = true, so when you bounce between tabs Compose Navigation
            // routes those through popEnter/popExit, not enter/exit. The
            // branches here mirror the forward animations exactly, so a
            // tab switch looks the same regardless of which direction the
            // user travels.
            popEnterTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                when {
                    from >= 0 && to >= 0 ->
                        slideInHorizontally(
                            initialOffsetX = { if (to > from) it else -it },
                            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing),
                        )
                    else -> fadeIn(tween(220, easing = LinearOutSlowInEasing))
                }
            },
            popExitTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                when {
                    // Settings closing: scale down + fade (app-close feel).
                    // Only when popping the hub back to a tab; popping a
                    // category back to the hub uses the depth slide below.
                    isSettings(initialState.destination.route) && to >= 0 ->
                        fadeOut(
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        ) + scaleOut(
                            targetScale = 0.7f,
                            animationSpec = spring(
                                stiffness = NavSpring,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        )
                    // Tab → tab restoration: directional slide matching the
                    // forward path so back-and-forth between tabs is uniform.
                    from >= 0 && to >= 0 ->
                        slideOutHorizontally(
                            targetOffsetX = { if (to > from) -it else it },
                            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing),
                        )
                    // Sub-route popping (session detail, accompagnateurs):
                    // slide out to the right.
                    else ->
                        fadeOut(tween(220, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(
                                targetOffsetX = { it / 3 },
                                animationSpec = tween(220, easing = FastOutLinearInEasing),
                            )
                }
            },
        ) {
            composable(Tab.TODAY.route) { TodayScreen() }
            composable(Tab.SESSIONS.route) {
                SessionsScreen(
                    onSessionClick = { id -> navController.navigate("session/$id") },
                )
            }
            composable(Tab.PROGRESS.route) { ProgressScreen() }
            composable(
                route = "session/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: return@composable
                SessionDetailScreen(
                    sessionId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(
                    onNavigateToProfile = { navController.navigate("settings/profile") },
                    onNavigateToApp = { navController.navigate("settings/app") },
                    onNavigateToMapReplay = { navController.navigate("settings/mapreplay") },
                    onNavigateToAbout = { navController.navigate("settings/about") },
                )
            }
            composable("settings/profile") {
                ProfileSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAccompagnateurs = {
                        navController.navigate("settings/accompagnateurs")
                    },
                )
            }
            composable("settings/app") {
                AppSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/mapreplay") {
                MapReplaySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/about") {
                AboutSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/accompagnateurs") {
                AccompagnateurManagementScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
