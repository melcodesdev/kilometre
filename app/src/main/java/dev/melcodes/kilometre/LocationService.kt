package dev.melcodes.kilometre

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.melcodes.kilometre.domain.AacMilestones
import dev.melcodes.kilometre.domain.Decision
import dev.melcodes.kilometre.domain.SessionLifecycle
import dev.melcodes.kilometre.domain.SessionRepository
import dev.melcodes.kilometre.domain.models.GpsPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Foreground service that subscribes to FusedLocationProviderClient at
// 1 Hz HIGH_ACCURACY and writes each accepted sample through the
// SessionRepository. The service owns no in-memory session state — the
// repository owns the DB, the lifecycle classifier owns the per-sample
// state machine, and this class is the thin glue that turns a
// LocationResult into an applySample() call.
//
// Started by SpikeScreen (Phase 1) and later by Today (Phase 1
// commit 7). The intent must carry EXTRA_SESSION_ID and
// EXTRA_STARTED_AT_MILLIS — the caller obtained both by awaiting
// SessionRepository.startSession() before sending the intent.
class LocationService : Service() {

    private lateinit var fused: FusedLocationProviderClient

    // The session this service instance is recording. Set on
    // ACTION_START, cleared on ACTION_STOP. The lifecycle classifier
    // and repository are pulled from KilometreApp.container once at
    // start so we don't re-resolve them on every sample.
    private var sessionId: Long = -1L
    private var lifecycle: SessionLifecycle? = null
    private var repository: SessionRepository? = null
    private var appScope: CoroutineScope? = null

    // True while the driver has manually paused. When paused we hold the
    // foreground service alive (notification stays) but unsubscribe from GPS,
    // so no distance accrues and no points are stored for the parked stretch.
    private var paused = false

    // Receives location updates from the fused provider. Runs on the
    // main looper. We do the (cheap) lifecycle classification inline,
    // then post the (potentially blocking) DB write to applicationScope.
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val sid = sessionId.takeIf { it > 0L } ?: return
            val life = lifecycle ?: return
            val repo = repository ?: return
            val scope = appScope ?: return

            for (loc in result.locations) {
                val point = GpsPoint(
                    sessionId = sid,
                    timestamp = Instant.fromEpochMilliseconds(loc.time),
                    lat = loc.latitude,
                    lng = loc.longitude,
                    altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
                    accuracyMeters = loc.accuracy,
                    speedMps = if (loc.hasSpeed()) loc.speed else null,
                    bearing = if (loc.hasBearing()) loc.bearing else null,
                )
                // Phase 1 only ever emits Decision.Accept. The when
                // is exhaustive over the sealed interface so adding
                // Reject in the future fails the compile here, not
                // silently at runtime.
                when (val decision = life.onSample(point)) {
                    is Decision.Accept -> {
                        scope.launch {
                            repo.applySample(sid, point, decision)
                            if (decision.autoStop) {
                                repo.stopSession(sid, endLat = point.lat, endLng = point.lng)
                                postMilestoneCheck()
                                stopSelf()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        val container = (application as KilometreApp).container
        repository = container.sessionRepository
        appScope = container.applicationScope
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sid = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                val startedAtMillis = intent.getLongExtra(EXTRA_STARTED_AT_MILLIS, -1L)
                if (sid <= 0L || startedAtMillis <= 0L) {
                    // Misuse — caller forgot to populate extras. We
                    // refuse rather than start a half-configured
                    // recording that would produce orphan GPS points.
                    Log.e(TAG, "ACTION_START without valid extras (sid=$sid, started=$startedAtMillis)")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startRecording(sid, Instant.fromEpochMilliseconds(startedAtMillis))
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> {
                // Finalize the session here too, not just in the in-app Stop
                // handler — otherwise a Stop tapped on the notification (e.g.
                // from the lock screen) would kill the service but leave the
                // row stuck ACTIVE. stopSession is idempotent, so the in-app
                // path calling it as well is harmless. The write goes on the
                // Application-scoped coroutine, which outlives stopSelf().
                val sid = sessionId
                if (sid > 0L) {
                    appScope?.launch { repository?.stopSession(sid) }
                }
                postMilestoneCheck()
                stopRecording()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startRecording(sid: Long, sessionStartedAt: Instant) {
        if (sessionId == sid) return  // already recording this session
        sessionId = sid
        paused = false
        lifecycle = SessionLifecycle(sessionStartedAt)

        val notification = buildNotification(paused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        requestLocationUpdates()
    }

    // Subscribe to the fused provider at 1 Hz. Shared by start and resume.
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Caller should have granted ACCESS_FINE_LOCATION before
            // sending ACTION_START. If not we abort cleanly.
            Log.e(TAG, "Missing location permission", e)
            stopRecording()
            stopSelf()
        }
    }

    // Manual pause: drop the GPS subscription so nothing is recorded for the
    // parked stretch, mark the row paused, and swap the notification to its
    // paused face (Resume + Stop). The service stays in the foreground.
    private fun pauseRecording() {
        val sid = sessionId.takeIf { it > 0L } ?: return
        if (paused) return
        paused = true
        fused.removeLocationUpdates(callback)
        appScope?.launch { repository?.pauseSession(sid) }
        updateNotification()
    }

    // Resume from a manual pause: a fresh SessionLifecycle so the straight
    // line across the parked gap is never counted as distance (its first
    // post-resume fix simply becomes the new reference), re-subscribe to GPS,
    // fold the pause span into pausedSeconds, and restore the notification.
    private fun resumeRecording() {
        val sid = sessionId.takeIf { it > 0L } ?: return
        if (!paused) return
        paused = false
        lifecycle = SessionLifecycle(Clock.System.now())
        appScope?.launch { repository?.resumeSession(sid) }
        requestLocationUpdates()
        updateNotification()
    }

    private fun stopRecording() {
        fused.removeLocationUpdates(callback)
        sessionId = -1L
        paused = false
        lifecycle = null
    }

    // Push the current notification face (recording vs paused) to the shade.
    private fun updateNotification() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(paused))
    }

    override fun onDestroy() {
        if (sessionId > 0L) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(paused: Boolean): Notification {
        // Each action fires its intent back into this same service, the same
        // paths the in-app buttons use. getService (not startService) is fine
        // because the service is already running in the foreground while the
        // notification is visible. FLAG_IMMUTABLE is required on API 31+ and
        // we never mutate these intents. Distinct request codes keep the three
        // PendingIntents from colliding.
        val stopPending = servicePendingIntent(REQ_STOP, ACTION_STOP)
        // While paused, the toggle resumes; while recording, it pauses.
        val togglePending =
            if (paused) servicePendingIntent(REQ_RESUME, ACTION_RESUME)
            else servicePendingIntent(REQ_PAUSE, ACTION_PAUSE)
        val toggleIcon = if (paused) R.drawable.ic_play else R.drawable.ic_pause
        val toggleLabel = getString(
            if (paused) R.string.notification_action_resume
            else R.string.notification_action_pause
        )
        val text = getString(
            if (paused) R.string.notification_text_paused else R.string.notification_text
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // VISIBILITY_PUBLIC so the actions stay tappable on the lock
            // screen without unlocking. Importance stays LOW so this does not
            // make the notification noisier.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(toggleIcon, toggleLabel, togglePending)
            .addAction(R.drawable.ic_stop, getString(R.string.notification_action_stop), stopPending)
            .build()
    }

    // Build a PendingIntent that re-delivers `action` to this service.
    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, LocationService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        mgr.createNotificationChannel(channel)

        // Separate channel for the AAC milestone reminder. IMPORTANCE_HIGH so
        // it heads-up — unlike the silent ongoing recording channel, this is a
        // one-shot the driver should actually see.
        val milestoneChannel = NotificationChannel(
            MILESTONE_CHANNEL_ID,
            getString(R.string.milestone_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        mgr.createNotificationChannel(milestoneChannel)
    }

    // After a drive finishes, check whether the new cumulative total crossed
    // the current AAC goal and, if so, post the rendez-vous-pédagogique
    // reminder. De-duped via AAC_NOTIFIED_KM so it fires once per milestone,
    // not after every later drive. Runs on the application scope (which
    // outlives stopSelf) and is a no-op unless AAC mode is on. The in-app
    // Progress card is the reliable acknowledgement path; this is the alert.
    private fun postMilestoneCheck() {
        val scope = appScope ?: return
        val container = (application as KilometreApp).container
        scope.launch {
            if (!container.aacModeEnabled.first()) return@launch
            val goal = container.driver.first()?.kmGoal ?: return@launch
            val complete = container.aacComplete.first()
            val notified = container.aacNotifiedKm.first()
            val totalKm = container.sessionRepository.totalDistanceMeters(1L).first() / 1000.0
            if (AacMilestones.isRdvDue(totalKm, goal, notified, complete)) {
                postMilestoneNotification(goal)
                container.markRdvNotified(goal)
            }
        }
    }

    private fun postMilestoneNotification(goal: Int) {
        // Tapping just opens the app; the goal advances from the in-app card,
        // not from the notification, so a stray dismiss never moves it.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this, REQ_MILESTONE_OPEN, openIntent, PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (AacMilestones.isFinalMilestone(goal)) {
            getString(R.string.notification_rdv_text_final)
        } else {
            getString(R.string.notification_rdv_text_first, goal)
        }
        val notification = NotificationCompat.Builder(this, MILESTONE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_rdv_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(MILESTONE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1

        // The AAC milestone reminder. Separate channel + id from the ongoing
        // recording notification so the two never overwrite each other.
        private const val MILESTONE_CHANNEL_ID = "milestone_channel"
        private const val MILESTONE_NOTIFICATION_ID = 2

        // Distinct PendingIntent request codes so the notification actions
        // don't overwrite one another.
        private const val REQ_STOP = 0
        private const val REQ_PAUSE = 1
        private const val REQ_RESUME = 2
        private const val REQ_MILESTONE_OPEN = 3

        const val ACTION_START = "dev.melcodes.kilometre.START"
        const val ACTION_STOP = "dev.melcodes.kilometre.STOP"
        const val ACTION_PAUSE = "dev.melcodes.kilometre.PAUSE"
        const val ACTION_RESUME = "dev.melcodes.kilometre.RESUME"
        const val EXTRA_SESSION_ID = "dev.melcodes.kilometre.SESSION_ID"
        const val EXTRA_STARTED_AT_MILLIS = "dev.melcodes.kilometre.STARTED_AT_MILLIS"

        // Convenience used by the UI to fire ACTION_START. Caller must
        // have already obtained the session via
        // SessionRepository.startSession().
        fun start(context: Context, sessionId: Long, startedAt: Instant) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_STARTED_AT_MILLIS, startedAt.toEpochMilliseconds())
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        // Pause/resume the running service. Safe to call only while a session
        // is recording; the service ignores them if it isn't.
        fun pause(context: Context) {
            context.startService(
                Intent(context, LocationService::class.java).apply { action = ACTION_PAUSE }
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, LocationService::class.java).apply { action = ACTION_RESUME }
            )
        }
    }
}
