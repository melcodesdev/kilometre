package dev.melcodes.kilometre

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File

// Application class. Constructed once by the Android runtime when
// any component (Activity, Service, BroadcastReceiver) in the app
// first launches. This is the only place AppContainer is built; from
// here Activities reach it by casting `applicationContext as
// KilometreApp` and reading `container`.
//
// This is also where cold-boot recovery
// lives. onCreate runs exactly once per process, so a Session row still
// marked ACTIVE here means the process died mid-recording (Android killed
// it, the phone rebooted, a crash). We take a fresh GPS fix: if the car
// is still moving at driving speed we silently relaunch LocationService
// to resume recording the same row; otherwise we finalize the orphan
// using its last recorded point as the honest end time.
//
// The Driver and Accompagnateur are no longer seeded here — real
// onboarding (AppContainer.completeOnboarding) creates them, and the
// UI gates the tabs behind onboarding completion so nothing reads them
// before they exist.
class KilometreApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        container = AppContainer(this)
        // Off the main thread: one-time data migrations first, then
        // cold-boot recovery. Order matters — the migrations never touch
        // the live ACTIVE row, so recovery sees an untouched session.
        container.applicationScope.launch {
            container.runOneTimeMigrationsIfNeeded()
            recoverInterruptedSessionIfAny()
        }
    }

    // Append uncaught exceptions to a local file so the user can share
    // them from Settings → About. The previous handler (usually Android's
    // default that shows the crash dialog) is called afterwards so system
    // behaviour is preserved.
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val log = File(filesDir, CRASH_LOG_FILE)
                log.appendText(
                    buildString {
                        append("\n--- ${java.time.Instant.now()} ---\n")
                        append("Thread: ${thread.name}\n")
                        append(throwable.stackTraceToString())
                        append("\n")
                    }
                )
            } catch (_: Throwable) {
                // Never let the crash logger itself crash the app silently.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    // If a session is still ACTIVE on cold boot, the process died while
    // recording it. Resume if the car is still moving, finalize if not.
    private suspend fun recoverInterruptedSessionIfAny() {
        val session = container.sessionRepository.resumeActiveIfAny() ?: return

        // No location permission means we can neither take a fix nor
        // record, so there's nothing to resume — finalize the orphan from
        // its last recorded point and move on.
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            container.sessionRepository.finalizeInterruptedSession(session.id)
            return
        }

        // A single fix usually carries a Doppler-derived speed. If it
        // doesn't (hasSpeed() false), we can't claim the car is moving, so
        // we treat it as stopped — better to finalize than to silently
        // resume a recording that may run forever against a parked phone.
        val location = currentLocationOrNull()
        val movingFast = location?.takeIf { it.hasSpeed() }
            ?.speed?.let { it >= RESUME_SPEED_FLOOR_MPS } ?: false

        if (movingFast) {
            // The process may have died while the session was manually paused.
            // We've just decided the car is moving again, so the dead-process
            // span isn't real pause time: drop the marker without crediting it
            // to pausedSeconds, otherwise the resumed UI would show "Paused"
            // while GPS is live.
            container.sessionRepository.clearManualPause(session.id)
            try {
                LocationService.start(this, session.id, session.startedAt)
            } catch (e: IllegalStateException) {
                // The background foreground-service start was blocked
                // (recovery ran from a non-foreground launch — e.g. boot).
                // ForegroundServiceStartNotAllowedException only exists
                // from API 31, so we catch its IllegalStateException
                // superclass to stay min-SDK-30 safe. Leave the row ACTIVE
                // so the next foreground launch retries recovery rather
                // than truncating a drive that's still happening.
                Log.w(TAG, "Could not resume recording from background", e)
            }
        } else {
            container.sessionRepository.finalizeInterruptedSession(session.id)
        }
    }

    // Await a single high-accuracy fix without pulling in the
    // kotlinx-coroutines-play-services artifact: bridge the Task by hand.
    // Permission is checked by the only caller before this runs, hence the
    // MissingPermission suppression. Returns null on failure or no fix.
    @SuppressLint("MissingPermission")
    private suspend fun currentLocationOrNull(): Location? {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    companion object {
        private const val TAG = "KilometreApp"
        const val CRASH_LOG_FILE = "crash_log.txt"

        // ~10 km/h. Above this on a fresh fix we treat the car as still
        // driving and resume; at or below it we finalize. Comfortably
        // above GPS jitter while parked, comfortably below any real drive.
        private const val RESUME_SPEED_FLOOR_MPS = 2.8f
    }
}
