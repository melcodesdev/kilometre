package dev.melcodes.kilometre.ui.today

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.LocationService
import dev.melcodes.kilometre.R
import kotlinx.coroutines.launch

// "Today" tab — the home screen. Two states:
//  - Idle: a friendly hero (location badge + invitation) and a Start button.
//  - Recording: a pulsing live dot, a big glanceable distance number, and a Stop button.
// Reads the active session from SessionRepository; distance updates live as samples land.
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()
    val activeSession by container.sessionRepository.activeSession
        .collectAsStateWithLifecycle(initialValue = null)
    val keepScreenOn by container.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)
    val totalMeters by container.sessionRepository.totalDistanceMeters(1L)
        .collectAsStateWithLifecycle(initialValue = 0.0)
    val driver by container.database.driverDao().observeDriver()
        .collectAsStateWithLifecycle(initialValue = null)
    var permissionError by remember { mutableStateOf(false) }

    // Hold the screen awake only while a session is actively recording and the
    // user has opted in. This is a window flag, not a service concern — the
    // recording runs in the foreground service regardless of the screen, so
    // FLAG_KEEP_SCREEN_ON is purely so the live distance stays glanceable on a
    // dashboard mount. Keyed on both signals so the flag is added when
    // recording starts (with the pref on) and cleared the moment either flips;
    // onDispose guarantees we never leak the flag if the screen leaves the tree.
    val view = LocalView.current
    val shouldKeepOn = keepScreenOn && activeSession != null
    DisposableEffect(shouldKeepOn) {
        val window = view.context.findActivity()?.window
        if (shouldKeepOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    fun startSessionAndService() {
        scope.launch {
            val accompId = container.database.accompagnateurDao()
                .firstForDriver(1L)?.id ?: return@launch
            val session = container.sessionRepository.startSession(
                driverId = 1L,
                accompagnateurId = accompId,
            )
            LocationService.start(context, session.id, session.startedAt)
        }
    }

    fun stopSessionAndService() {
        val active = activeSession ?: return
        scope.launch {
            container.sessionRepository.stopSession(active.id)
            LocationService.stop(context)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            permissionError = false
            startSessionAndService()
        } else {
            permissionError = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // weight(1f) spacers above and below the hero push it to the
        // vertical centre while leaving the button pinned at the bottom.
        Spacer(Modifier.weight(1f))

        val active = activeSession
        if (active != null) {
            RecordingHero(
                distanceKm = active.distanceMeters / 1000.0,
                sessionId = active.id,
                paused = active.manualPauseStartedAt != null,
            )
        } else {
            IdleHero(
                totalKm = totalMeters / 1000.0,
                goalKm = driver?.kmGoal ?: 3000,
            )
        }

        if (permissionError) {
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.permission_denied),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        if (active != null) {
            // Pause/Resume routes through the foreground service so it can
            // actually stop and restart GPS updates; the service owns the DB
            // write (manualPauseStartedAt) so there is a single writer. The
            // button label/icon follows the live paused state off the row.
            val paused = active.manualPauseStartedAt != null
            FilledTonalButton(
                onClick = {
                    if (paused) LocationService.resume(context)
                    else LocationService.pause(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (paused) R.drawable.ic_play else R.drawable.ic_pause
                    ),
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (paused) R.string.resume_recording else R.string.pause_recording
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = { stopSessionAndService() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(
                    text = stringResource(R.string.stop_recording),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Button(
                onClick = {
                    val missing = permissions.any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing) {
                        launcher.launch(permissions)
                    } else {
                        permissionError = false
                        startSessionAndService()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.start_recording),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// Walk the ContextWrapper chain to the hosting Activity. Compose's LocalView
// context is a ContextThemeWrapper, not the Activity directly, so we unwrap it
// to reach the window for the keep-screen-on flag. Returns null if no Activity
// is found (should not happen for an on-screen composable).
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Idle state: a calm location badge, an invitation to start a drive, and a
// compact one-liner showing how far the driver is toward their goal. The
// progress line only appears once there is at least one completed session,
// so a brand-new install doesn't show "0.0 km toward your goal".
@Composable
private fun IdleHero(totalKm: Double, goalKm: Int) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp),
        )
    }
    Spacer(Modifier.size(24.dp))
    Text(
        text = stringResource(R.string.today_ready_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.today_ready_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (totalKm > 0.0) {
        Spacer(Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.today_progress_summary, totalKm, goalKm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

// Recording state: a pulsing dot signals live capture, then the big distance
// number. When paused the dot stops pulsing and dims to a neutral colour, and
// the label flips to "Paused" — the distance freezes because GPS is off.
@Composable
private fun RecordingHero(distanceKm: Double, sessionId: Long, paused: Boolean) {
    // rememberInfiniteTransition drives a value that loops forever while
    // this composable is on screen — here it fades the dot in and out.
    val transition = rememberInfiniteTransition(label = "recording-pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { alpha = if (paused) 0.6f else pulseAlpha }
                .clip(CircleShape)
                .background(
                    if (paused) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                ),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(
                if (paused) R.string.today_paused_label else R.string.today_recording_label
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.size(16.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = stringResource(R.string.today_distance_value, distanceKm),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.today_km_unit),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    Spacer(Modifier.size(8.dp))
    Text(
        text = stringResource(R.string.status_session, sessionId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
