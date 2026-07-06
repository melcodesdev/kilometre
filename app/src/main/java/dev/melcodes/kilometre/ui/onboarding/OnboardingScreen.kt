package dev.melcodes.kilometre.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import kotlinx.coroutines.launch
import java.util.Locale

private const val TOTAL_STEPS = 6

// First-launch onboarding, blocking and one-time. The user cannot reach the tab shell until this completes, because
// the tabs assume a Driver and an Accompagnateur exist.
//
// There is no explicit "finished" callback: the final step writes the
// onboarding-complete flag via AppContainer.completeOnboarding, and
// MainActivity observes that flag and swaps this screen out for the tab
// shell automatically.
//
// Visual direction is "subtle craft" (feedback memory 2026-05-30): flat and
// restrained, no glow/gradient/chip decoration, but not basic either. The
// craft comes from a flat solid hero badge, left-aligned confident type, one
// quiet background shape, and a gentle entrance: each step's content eases up
// and fades in once when it appears.
//
// Steps:
//   0. Language — English or French. Tapping applies it immediately via
//      AppCompatDelegate.setApplicationLocales, which recreates the activity
//      so the rest of onboarding renders in the chosen language.
//   1. Welcome — what the app is, plus the privacy promise in one prose line.
//   2. Driver name.
//   3. Accompagnateur name + relationship.
//   4. Tracking mode — follow the AAC milestones (RDV reminders, goal cycles
//      1000 → 3000) or simple kilometre tracking. Sets the starting goal.
//   5. Permissions — request location + notifications, then finish.
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()

    // mutableIntStateOf is the Int-specialised state holder (avoids boxing
    // the step counter on every recomposition). `by` delegates so `step`
    // reads/writes like a plain var while staying observable.
    var step by remember { mutableIntStateOf(0) }
    var driverName by remember { mutableStateOf("") }
    var accompagnateurName by remember { mutableStateOf("") }
    var accompagnateurRelation by remember { mutableStateOf("") }
    // Tracking-mode step (4). AAC pre-selected — it's the app's reason to
    // exist; a user who only wants a counter switches to simple here.
    var aacMode by remember { mutableStateOf(true) }

    // The language applied to this composition. Empty app-locales means the
    // user has not chosen yet, so we fall back to the system language; if that
    // is neither French nor English we default the highlight to English (the
    // default string resources). Recomputed after each activity recreate, so
    // it always reflects the locale currently in force.
    val appliedLang = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        val lang = if (locales.isEmpty) Locale.getDefault().language else locales[0]?.language
        if (lang == "fr") "fr" else "en"
    }
    // Applying a language recreates the activity, which restarts onboarding at
    // step 0 in the new language. Guard against re-applying the active one so
    // tapping the already-selected language is a no-op (no needless recreate).
    val selectLanguage: (String) -> Unit = { tag ->
        if (tag != appliedLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    // Requested at the final step. Same set TodayScreen asks for at START,
    // so by the time the user records, the grant is already in place.
    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // Whatever the user grants or denies, onboarding completes — the app
    // still works without the permissions (Today re-requests them), and a
    // forced dead-end here would be worse UX. Persist the answers, flip
    // the flag; MainActivity routes to the tab shell on the next emission.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            container.completeOnboarding(
                driverName = driverName.trim(),
                accompagnateurName = accompagnateurName.trim(),
                accompagnateurRelation = accompagnateurRelation.trim(),
                aacMode = aacMode,
            )
        }
    }

    // Whether the primary button is tappable on the current step. The form
    // steps require their fields filled; welcome and permissions are always
    // enabled.
    val canAdvance = when (step) {
        2 -> driverName.isNotBlank()
        3 -> accompagnateurName.isNotBlank() && accompagnateurRelation.isNotBlank()
        else -> true
    }

    val primaryLabel = when (step) {
        1 -> R.string.onboarding_get_started
        5 -> R.string.onboarding_finish
        else -> R.string.onboarding_continue
    }

    // Surface paints colorScheme.background and sets the default content
    // colour to onBackground, so everything below renders on the app's
    // near-black background with light text.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // One quiet background shape: a large circle at very low opacity,
            // tucked mostly off the top-right corner. Flat (no gradient, no
            // glow) — it just keeps the near-black field from feeling empty.
            // Sits behind the content because it is drawn first in the Box.
            //
            // It drifts as the user advances: each step nudges it left and
            // down, and animateDpAsState glides it there over ~480ms — a touch
            // slower than the 260ms content entrance, so the circle trails the
            // text for a parallax "sliding with the transition" feel. Default
            // tween easing (FastOutSlowIn) gives the satisfying ease-out settle.
            val circleX by animateDpAsState(
                targetValue = (200 - step * 26).dp,
                animationSpec = tween(480),
                label = "circleX",
            )
            val circleY by animateDpAsState(
                targetValue = (-140 + step * 22).dp,
                animationSpec = tween(480),
                label = "circleY",
            )
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .offset(x = circleX, y = circleY)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        CircleShape,
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                // Step progress: a thin bar plus an "n of 5" label. Gives the
                // flow a sense of length so it doesn't feel like an endless form.
                LinearProgressIndicator(
                    progress = { (step + 1) / TOTAL_STEPS.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_step_counter, step + 1, TOTAL_STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Content area with a gentle entrance: a fresh Animatable per
                // step (keyed on `step`) runs 0 -> 1, driving alpha and a small
                // upward translation so each step's content fades in and settles
                // up into place. Directional, not a dissolve.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val enter = remember(step) { Animatable(0f) }
                    LaunchedEffect(step) { enter.animateTo(1f, tween(260)) }
                    // Top-aligned + scroll: when the keyboard opens on the
                    // two-field accompagnateur step, every field stays reachable
                    // (centring clipped the hero and buried the second field —
                    // feedback memory).
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = enter.value
                                translationY = (1f - enter.value) * 24.dp.toPx()
                            }
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        when (step) {
                            0 -> {
                                // No icon badge here: the two big language rows
                                // are the hero, matching how system setup flows
                                // present a language pick.
                                StepHeader(
                                    icon = null,
                                    title = stringResource(R.string.onboarding_language_title),
                                    subtitle = stringResource(R.string.onboarding_language_subtitle),
                                )
                                LanguageOption(
                                    label = stringResource(R.string.onboarding_language_english),
                                    selected = appliedLang == "en",
                                    onClick = { selectLanguage("en") },
                                )
                                LanguageOption(
                                    label = stringResource(R.string.onboarding_language_french),
                                    selected = appliedLang == "fr",
                                    onClick = { selectLanguage("fr") },
                                )
                            }

                            1 -> StepHeader(
                                icon = Icons.Default.LocationOn,
                                title = stringResource(R.string.onboarding_welcome_title),
                                subtitle = stringResource(R.string.onboarding_welcome_body),
                            )

                            2 -> {
                                StepHeader(
                                    icon = Icons.Default.Person,
                                    title = stringResource(R.string.onboarding_driver_title),
                                    subtitle = stringResource(R.string.onboarding_driver_subtitle),
                                )
                                OutlinedTextField(
                                    value = driverName,
                                    onValueChange = { driverName = it },
                                    label = { Text(stringResource(R.string.onboarding_driver_name_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            3 -> {
                                StepHeader(
                                    icon = Icons.Default.Person,
                                    title = stringResource(R.string.onboarding_accompagnateur_title),
                                    subtitle = stringResource(R.string.onboarding_accompagnateur_subtitle),
                                )
                                OutlinedTextField(
                                    value = accompagnateurName,
                                    onValueChange = { accompagnateurName = it },
                                    label = { Text(stringResource(R.string.onboarding_accompagnateur_name_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = accompagnateurRelation,
                                    onValueChange = { accompagnateurRelation = it },
                                    label = { Text(stringResource(R.string.onboarding_accompagnateur_relation_label)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            4 -> {
                                StepHeader(
                                    icon = Icons.Default.Check,
                                    title = stringResource(R.string.onboarding_tracking_title),
                                    subtitle = stringResource(R.string.onboarding_tracking_subtitle),
                                )
                                LanguageOption(
                                    label = stringResource(R.string.onboarding_tracking_aac),
                                    selected = aacMode,
                                    onClick = { aacMode = true },
                                )
                                LanguageOption(
                                    label = stringResource(R.string.onboarding_tracking_simple),
                                    selected = !aacMode,
                                    onClick = { aacMode = false },
                                )
                            }

                            else -> StepHeader(
                                icon = Icons.Default.LocationOn,
                                title = stringResource(R.string.onboarding_permissions_title),
                                subtitle = stringResource(R.string.onboarding_permissions_body),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        when (step) {
                            0, 1, 2, 3, 4 -> step += 1
                            else -> permissionLauncher.launch(permissions)
                        }
                    },
                    enabled = canAdvance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(stringResource(primaryLabel))
                }

                if (step > 0) {
                    TextButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
            }
        }
    }
}

// Shared header for every step: a flat solid icon badge, a title, and a
// one-line subtitle, all left-aligned. The badge is a plain primaryContainer
// circle with the icon — no glow or gradient behind it (that read as
// AI-ish); the colour fill alone carries it. icon is nullable: the language
// step passes null and skips the badge, since its two option rows are the hero.
@Composable
private fun StepHeader(icon: ImageVector?, title: String, subtitle: String) {
    if (icon != null) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// One selectable language row on the language step. Flat: a rounded outline
// that thickens and switches to the primary colour when selected, plus a check
// on the right. No fill/gradient/chip — it reads like a settings list row.
@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
