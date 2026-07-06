package dev.melcodes.kilometre

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.melcodes.kilometre.data.db.KilometreDatabase
import dev.melcodes.kilometre.domain.AacMilestones
import dev.melcodes.kilometre.domain.SessionRepository
import dev.melcodes.kilometre.domain.models.Accompagnateur
import dev.melcodes.kilometre.domain.models.Driver
import dev.melcodes.kilometre.domain.models.DrivingScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Process-singleton Preferences DataStore. The delegate must live at the
// top level so only one instance backs the "kilometre_prefs" file.
private val Context.kilometrePrefs: DataStore<Preferences> by preferencesDataStore(
    name = "kilometre_prefs",
)

// Flag marking the one-time distance recompute (2026-05-29 fix) as done,
// so it runs exactly once after the upgrade and not on every launch.
private val DISTANCE_RECOMPUTE_V1_DONE = booleanPreferencesKey("distance_recompute_v1_done")

// Flag marking the one-time sweep of empty (zero-distance) sessions as
// done. Clears rows recorded before stopSession learned to discard them.
private val ZERO_DISTANCE_CLEANUP_V1_DONE = booleanPreferencesKey("zero_distance_cleanup_v1_done")

// True once the user has finished the onboarding flow. Until then the
// app shows OnboardingScreen instead of the three-tab shell (the tabs
// assume a Driver and an Accompagnateur exist).
// Flag marking the one-time backfill of route snapshot thumbnails as
// done. Generates WebPs for sessions recorded before the feature landed.
private val ROUTE_SNAPSHOT_BACKFILL_V1_DONE = booleanPreferencesKey("route_snapshot_backfill_v1_done")

private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

// AAC milestone tracking (2026-06-11). When AAC_MODE_ENABLED is on, the
// driver's kmGoal cycles through AacMilestones.LADDER (1000 → 3000) and a
// rendez-vous-pédagogique reminder fires each time the total crosses the
// current goal. AAC_NOTIFIED_KM is the milestone we last posted a notification
// for (0 = none), so a notification fires once per milestone rather than after
// every subsequent drive. AAC_COMPLETE is set once the final (3000 km) RDV has
// been acknowledged, so the in-app card stops showing. Default off so an
// existing install's manually-chosen goal is left untouched until opt-in.
private val AAC_MODE_ENABLED = booleanPreferencesKey("aac_mode_enabled")
private val AAC_NOTIFIED_KM = intPreferencesKey("aac_notified_km")
private val AAC_COMPLETE = booleanPreferencesKey("aac_complete")

// User preferences — theme, map appearance, route gradient.
private val APP_THEME = stringPreferencesKey("app_theme")          // "system"|"light"|"dark"
private val MAP_STYLE = stringPreferencesKey("map_style")          // "liberty"|"bright"|"positron"|"fiord"
private val GRADIENT_START_HEX = stringPreferencesKey("gradient_start_hex") // AARRGGBB hex
private val GRADIENT_END_HEX = stringPreferencesKey("gradient_end_hex")

// When true, the Today screen holds the screen awake while a session is
// recording so the live distance stays glanceable on a dashboard mount.
// Default false: the recording itself runs in the foreground service and is
// unaffected by the screen sleeping, so keeping it on is purely a viewing aid.
private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

// Which series the elevation/speed chart shows. Remembered so reopening the
// replay drawer (or another session's chart) keeps the user's last choice
// instead of always reverting to altitude-only. Defaults: altitude on, speed
// off — the same first-run defaults as before.
private val CHART_SHOW_ALTITUDE = booleanPreferencesKey("chart_show_altitude")
private val CHART_SHOW_SPEED = booleanPreferencesKey("chart_show_speed")

// ── Map / replay / chart preferences (Settings → Replay & Chart) ──────
// How long a full replay takes, in seconds (the fixed-length choice). Ignored
// when REPLAY_SCALE_TO_DISTANCE is on.
private val REPLAY_DURATION_SECONDS = intPreferencesKey("replay_duration_seconds")
// When on, the replay duration scales with the drive length (~1 s per km,
// clamped) instead of using the fixed seconds above — so long drives don't blur.
private val REPLAY_SCALE_TO_DISTANCE = booleanPreferencesKey("replay_scale_to_distance")
// Start replays already following the dot.
private val REPLAY_FOLLOW_DEFAULT = booleanPreferencesKey("replay_follow_default")
// Playback rate a replay opens at (one of the presets 0.25..2).
private val REPLAY_DEFAULT_SPEED = floatPreferencesKey("replay_default_speed")
// Restart the replay automatically when it reaches the end.
private val REPLAY_LOOP = booleanPreferencesKey("replay_loop")
// Half-width (in samples ≈ seconds at 1 Hz) of the altitude moving-average.
// Bigger = smoother. 7 ≈ 15 s, 15 ≈ 31 s, 31 ≈ 63 s windows.
private val ALTITUDE_SMOOTHING_HALF = intPreferencesKey("altitude_smoothing_half")
// Same, for the speed curve. Default 7 ≈ 15 s window.
private val SPEED_SMOOTHING_HALF = intPreferencesKey("speed_smoothing_half")
// Draw the dual-axis gridlines on the elevation/speed chart.
private val CHART_SHOW_GRID = booleanPreferencesKey("chart_show_grid")
// Customisable chart colours (RRGGBB hex): altitude curve, speed curve, dot.
private val CHART_SPEED_HEX = stringPreferencesKey("chart_speed_hex")
private val REPLAY_DOT_HEX = stringPreferencesKey("replay_dot_hex")
private val CHART_ALTITUDE_HEX = stringPreferencesKey("chart_altitude_hex")

// SAF tree URI of the folder the user picked for "quick save" of GPX
// exports. Absent until they choose one in Settings. We persist only the
// URI string; the actual read/write grant is held by the system after a
// takePersistableUriPermission call at pick time, so it survives reboots
// without any storage permission in the manifest.
private val DEFAULT_SAVE_TREE_URI = stringPreferencesKey("default_save_tree_uri")

// Defaults match the current hardcoded values in RouteSnapshot and SessionDetailScreen.
internal const val DEFAULT_GRADIENT_START_HEX = "FF493F59"
internal const val DEFAULT_GRADIENT_END_HEX = "FFD0BCFF"

// Default chart colours (RRGGBB) — match StatAccentAmber / StatAccentBlue /
// StatAccentPurple.
internal const val DEFAULT_CHART_SPEED_HEX = "E0A02E"
internal const val DEFAULT_REPLAY_DOT_HEX = "4C8DF5"
internal const val DEFAULT_CHART_ALTITUDE_HEX = "9B7BE8"

// Manual dependency-injection root for the app. Constructed exactly
// once by KilometreApp.onCreate. Holds the singletons that outlive
// any Activity or Service: the Room database, the repositories, and
// a process-wide CoroutineScope for fire-and-forget DB writes from
// the foreground service callback.
//
// No Hilt for now. This class is the
// alternative: a plain Kotlin object you pass around manually. The
// day a real DI graph becomes worth its complexity we migrate; not
// before.
class AppContainer(context: Context) {
    val database: KilometreDatabase = KilometreDatabase.build(context)

    private val dataStore: DataStore<Preferences> = context.kilometrePrefs

    // Kept on the container so SessionCard can invalidate both the on-disk
    // WebPs and the in-memory ImageBitmap cache when the gradient changes.
    val cacheDir: File = context.cacheDir

    val sessionRepository: SessionRepository = SessionRepository(
        sessionDao = database.sessionDao(),
        gpsPointDao = database.gpsPointDao(),
        cacheDir = context.cacheDir,
    )

    // Process-wide cache of decoded route-thumbnail ImageBitmaps, keyed by
    // session id. Lives on the container (Application-scoped) so it survives
    // leaving and re-entering the Sessions tab — otherwise every visit
    // re-decodes every WebP and the cards flash blank for a frame.
    // Cleared when the gradient changes; the disk WebPs are wiped in the
    // same step so SessionCard regenerates on next view.
    val routeThumbnailCache: ConcurrentHashMap<Long, ImageBitmap> = ConcurrentHashMap()

    private suspend fun invalidateRouteThumbnails() {
        routeThumbnailCache.clear()
        withContext(Dispatchers.IO) {
            val dir = File(cacheDir, "route_thumbs")
            if (dir.isDirectory) dir.listFiles()?.forEach { it.delete() }
        }
    }

    // Process-wide scope for the foreground service's per-sample DB
    // writes. SupervisorJob means one failed write doesn't cancel
    // sibling coroutines; IO dispatcher because every operation here
    // is a Room call.
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // True once onboarding has been completed. The UI gates on this:
    // false sends the user to OnboardingScreen, true to the tab shell.
    // A cold Flow over DataStore, so MainActivity re-renders the moment
    // completeOnboarding() flips the flag.
    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    // AAC milestone state. See the AAC_* keys above. The Progress card and the
    // milestone notification read these; the Settings toggle and the card's
    // "J'ai compris" button write them through the methods below.
    val aacModeEnabled: Flow<Boolean> =
        dataStore.data.map { it[AAC_MODE_ENABLED] ?: false }
    val aacNotifiedKm: Flow<Int> =
        dataStore.data.map { it[AAC_NOTIFIED_KM] ?: 0 }
    val aacComplete: Flow<Boolean> =
        dataStore.data.map { it[AAC_COMPLETE] ?: false }

    // User-controlled appearance preferences. Defaults are the values
    // previously hardcoded in the map and snapshot code.
    val appTheme: Flow<String> =
        dataStore.data.map { it[APP_THEME] ?: "system" }
    val mapStyle: Flow<String> =
        dataStore.data.map { it[MAP_STYLE] ?: "liberty" }
    val gradientStartHex: Flow<String> =
        dataStore.data.map { it[GRADIENT_START_HEX] ?: DEFAULT_GRADIENT_START_HEX }
    val gradientEndHex: Flow<String> =
        dataStore.data.map { it[GRADIENT_END_HEX] ?: DEFAULT_GRADIENT_END_HEX }

    // Whether to hold the screen awake during recording. See KEEP_SCREEN_ON.
    val keepScreenOn: Flow<Boolean> =
        dataStore.data.map { it[KEEP_SCREEN_ON] ?: false }

    // Remembered elevation/speed chart series selection. See CHART_SHOW_*.
    val chartShowAltitude: Flow<Boolean> =
        dataStore.data.map { it[CHART_SHOW_ALTITUDE] ?: true }
    val chartShowSpeed: Flow<Boolean> =
        dataStore.data.map { it[CHART_SHOW_SPEED] ?: false }

    // Map / replay / chart preferences with their first-run defaults.
    val replayDurationSeconds: Flow<Int> =
        dataStore.data.map { it[REPLAY_DURATION_SECONDS] ?: 20 }
    val replayScaleToDistance: Flow<Boolean> =
        dataStore.data.map { it[REPLAY_SCALE_TO_DISTANCE] ?: false }
    val replayFollowDefault: Flow<Boolean> =
        dataStore.data.map { it[REPLAY_FOLLOW_DEFAULT] ?: false }
    val replayDefaultSpeed: Flow<Float> =
        dataStore.data.map { it[REPLAY_DEFAULT_SPEED] ?: 1f }
    val replayLoop: Flow<Boolean> =
        dataStore.data.map { it[REPLAY_LOOP] ?: false }
    val altitudeSmoothingHalf: Flow<Int> =
        dataStore.data.map { it[ALTITUDE_SMOOTHING_HALF] ?: 15 }
    val speedSmoothingHalf: Flow<Int> =
        dataStore.data.map { it[SPEED_SMOOTHING_HALF] ?: 7 }
    val chartShowGrid: Flow<Boolean> =
        dataStore.data.map { it[CHART_SHOW_GRID] ?: true }
    val chartSpeedHex: Flow<String> =
        dataStore.data.map { it[CHART_SPEED_HEX] ?: DEFAULT_CHART_SPEED_HEX }
    val replayDotHex: Flow<String> =
        dataStore.data.map { it[REPLAY_DOT_HEX] ?: DEFAULT_REPLAY_DOT_HEX }
    val chartAltitudeHex: Flow<String> =
        dataStore.data.map { it[CHART_ALTITUDE_HEX] ?: DEFAULT_CHART_ALTITUDE_HEX }

    // The user's chosen quick-save folder as a SAF tree URI string, or null
    // if none is set. The session detail screen shows the "Quick save" menu
    // entry only when this is non-null.
    val defaultSaveTreeUri: Flow<String?> =
        dataStore.data.map { it[DEFAULT_SAVE_TREE_URI] }

    // Live views of the Driver singleton and all accompagnateurs for the
    // settings screen. Both are read-only from the UI — mutations go
    // through the typed update methods below so callers can't accidentally
    // wipe FK-referenced fields.
    val driver: Flow<dev.melcodes.kilometre.domain.models.Driver?> =
        database.driverDao().observeDriver()
    val accompagnateurs: Flow<List<dev.melcodes.kilometre.domain.models.Accompagnateur>> =
        database.accompagnateurDao().observeForDriver(1L)

    suspend fun setAppTheme(value: String) {
        dataStore.edit { it[APP_THEME] = value }
    }

    suspend fun setMapStyle(value: String) {
        dataStore.edit { it[MAP_STYLE] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = value }
    }

    suspend fun setChartShowAltitude(value: Boolean) {
        dataStore.edit { it[CHART_SHOW_ALTITUDE] = value }
    }

    suspend fun setChartShowSpeed(value: Boolean) {
        dataStore.edit { it[CHART_SHOW_SPEED] = value }
    }

    suspend fun setReplayDurationSeconds(value: Int) {
        dataStore.edit { it[REPLAY_DURATION_SECONDS] = value }
    }

    suspend fun setReplayScaleToDistance(value: Boolean) {
        dataStore.edit { it[REPLAY_SCALE_TO_DISTANCE] = value }
    }

    suspend fun setReplayFollowDefault(value: Boolean) {
        dataStore.edit { it[REPLAY_FOLLOW_DEFAULT] = value }
    }

    suspend fun setReplayDefaultSpeed(value: Float) {
        dataStore.edit { it[REPLAY_DEFAULT_SPEED] = value }
    }

    suspend fun setReplayLoop(value: Boolean) {
        dataStore.edit { it[REPLAY_LOOP] = value }
    }

    suspend fun setAltitudeSmoothingHalf(value: Int) {
        dataStore.edit { it[ALTITUDE_SMOOTHING_HALF] = value }
    }

    suspend fun setSpeedSmoothingHalf(value: Int) {
        dataStore.edit { it[SPEED_SMOOTHING_HALF] = value }
    }

    suspend fun setChartShowGrid(value: Boolean) {
        dataStore.edit { it[CHART_SHOW_GRID] = value }
    }

    suspend fun setChartSpeedHex(hex: String) {
        dataStore.edit { it[CHART_SPEED_HEX] = hex }
    }

    suspend fun setReplayDotHex(hex: String) {
        dataStore.edit { it[REPLAY_DOT_HEX] = hex }
    }

    suspend fun setChartAltitudeHex(hex: String) {
        dataStore.edit { it[CHART_ALTITUDE_HEX] = hex }
    }

    suspend fun setGradientStartHex(hex: String) {
        invalidateRouteThumbnails()
        dataStore.edit { it[GRADIENT_START_HEX] = hex }
    }

    suspend fun setGradientEndHex(hex: String) {
        invalidateRouteThumbnails()
        dataStore.edit { it[GRADIENT_END_HEX] = hex }
    }

    // Store the picked quick-save folder, or clear it when uri is null. The
    // persistable read/write grant must already have been taken by the
    // caller (Settings) before this is stored.
    suspend fun setDefaultSaveTreeUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(DEFAULT_SAVE_TREE_URI) else it[DEFAULT_SAVE_TREE_URI] = uri
        }
    }

    suspend fun updateDriverName(name: String) {
        val dao = database.driverDao()
        dao.getDriver()?.let { dao.update(it.copy(name = name)) }
    }

    suspend fun updateDriverKmGoal(kmGoal: Int) {
        val dao = database.driverDao()
        dao.getDriver()?.let { dao.update(it.copy(kmGoal = kmGoal)) }
    }

    // Turn on AAC milestone tracking. Snaps the goal to the lowest milestone
    // the driver hasn't passed yet (1000 fresh, or 3000 if they're already
    // past 1000) and clears the notified/complete flags so the cycle restarts
    // cleanly from wherever they actually are.
    suspend fun enableAacMode() {
        val totalKm = sessionRepository.totalDistanceMeters(1L).first() / 1000.0
        val goal = AacMilestones.firstUnreachedMilestone(totalKm)
        val dao = database.driverDao()
        dao.getDriver()?.let { dao.update(it.copy(kmGoal = goal)) }
        dataStore.edit {
            it[AAC_MODE_ENABLED] = true
            it[AAC_NOTIFIED_KM] = 0
            it[AAC_COMPLETE] = false
        }
    }

    // Turn off AAC milestone tracking. The goal is left where it is — the user
    // edits it manually from then on (Profile → AAC goal), so we don't surprise
    // them by resetting it.
    suspend fun disableAacMode() {
        dataStore.edit { it[AAC_MODE_ENABLED] = false }
    }

    // The user tapped "J'ai compris" on the RDV card. Advance the goal to the
    // next milestone, or mark the journey complete if this was the final one.
    suspend fun acknowledgeRdvMilestone() {
        val dao = database.driverDao()
        val driver = dao.getDriver() ?: return
        val next = AacMilestones.nextGoalAfter(driver.kmGoal)
        if (next == null) {
            dataStore.edit { it[AAC_COMPLETE] = true }
        } else {
            dao.update(driver.copy(kmGoal = next))
        }
    }

    // Record that the RDV notification for `km` has been posted, so the
    // milestone check (run on every stop) doesn't repost it on later drives.
    suspend fun markRdvNotified(km: Int) {
        dataStore.edit { it[AAC_NOTIFIED_KM] = km }
    }

    suspend fun addAccompagnateur(name: String, relation: String): Long {
        return database.accompagnateurDao().insert(
            Accompagnateur(
                driverId = 1L,
                name = name,
                relation = relation,
                createdAt = Clock.System.now(),
            ),
        )
    }

    suspend fun updateAccompagnateur(acc: Accompagnateur) {
        database.accompagnateurDao().update(acc)
    }

    suspend fun deleteAccompagnateur(acc: Accompagnateur) {
        database.accompagnateurDao().delete(acc)
    }

    // Delete one finished session (the detail-screen trash action). The
    // repository removes the row + its GPS points + the on-disk thumbnail;
    // we then evict the decoded thumbnail held in memory here so the
    // Sessions list doesn't briefly render a card for a session that's gone.
    suspend fun deleteSession(id: Long) {
        sessionRepository.deleteSession(id)
        routeThumbnailCache.remove(id)
    }

    // Persist the onboarding answers and mark onboarding done. Establishes
    // the singleton Driver (id = 1) and the first Accompagnateur, then
    // flips the flag. D1 (2026-05-28) fixes scheme = AAC and goal = 3000
    // for v0.1, so onboarding does not ask for them. birthdate is the D5
    // placeholder until Phase 6 needs it.
    //
    // Both writes are update-if-exists rather than REPLACE-upsert: on an
    // install that already recorded sessions (the dev phone with the
    // recovered 18 km drive), the existing rows are referenced by foreign
    // keys, so they must be edited in place, not deleted and reinserted.
    suspend fun completeOnboarding(
        driverName: String,
        accompagnateurName: String,
        accompagnateurRelation: String,
        aacMode: Boolean,
    ) {
        val driverDao = database.driverDao()
        val accompagnateurDao = database.accompagnateurDao()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

        // In AAC mode the goal starts on the first milestone the driver hasn't
        // reached (1000 fresh); in simple mode it's the full 3000 km AAC total
        // as a single static target the user can edit later.
        val totalKm = sessionRepository.totalDistanceMeters(1L).first() / 1000.0
        val goal = if (aacMode) AacMilestones.firstUnreachedMilestone(totalKm) else 3000

        val existingDriver = driverDao.getDriver()
        if (existingDriver == null) {
            driverDao.upsert(
                Driver(
                    id = 1L,
                    name = driverName,
                    birthdate = LocalDate(2000, 1, 1),
                    scheme = DrivingScheme.AAC,
                    startDate = today,
                    kmGoal = goal,
                    createdAt = now,
                ),
            )
        } else {
            // Existing install: keep the manually-set goal in simple mode, but
            // snap it to the current milestone if they chose AAC mode.
            val updated = if (aacMode) {
                existingDriver.copy(name = driverName, kmGoal = goal)
            } else {
                existingDriver.copy(name = driverName)
            }
            driverDao.update(updated)
        }

        val existingAccompagnateur = accompagnateurDao.firstForDriver(1L)
        if (existingAccompagnateur == null) {
            accompagnateurDao.insert(
                Accompagnateur(
                    driverId = 1L,
                    name = accompagnateurName,
                    relation = accompagnateurRelation,
                    createdAt = now,
                ),
            )
        } else {
            accompagnateurDao.update(
                existingAccompagnateur.copy(
                    name = accompagnateurName,
                    relation = accompagnateurRelation,
                ),
            )
        }

        dataStore.edit {
            it[AAC_MODE_ENABLED] = aacMode
            it[AAC_NOTIFIED_KM] = 0
            it[AAC_COMPLETE] = false
            it[ONBOARDING_COMPLETE] = true
        }
    }

    // One-time data migrations that aren't schema changes (so they can't
    // be Room migrations). Each is guarded by its own DataStore flag and
    // runs at most once. Called off the main thread from KilometreApp.
    suspend fun runOneTimeMigrationsIfNeeded() {
        val prefs = dataStore.data.first()

        val recomputeDone = prefs[DISTANCE_RECOMPUTE_V1_DONE] ?: false
        if (!recomputeDone) {
            sessionRepository.recomputeAllDistances()
            dataStore.edit { it[DISTANCE_RECOMPUTE_V1_DONE] = true }
        }

        // Run after the recompute: a session whose real distance was
        // recovered above must not be swept here, so order matters.
        val cleanupDone = prefs[ZERO_DISTANCE_CLEANUP_V1_DONE] ?: false
        if (!cleanupDone) {
            sessionRepository.deleteZeroDistanceSessions()
            dataStore.edit { it[ZERO_DISTANCE_CLEANUP_V1_DONE] = true }
        }

        val snapshotDone = prefs[ROUTE_SNAPSHOT_BACKFILL_V1_DONE] ?: false
        if (!snapshotDone) {
            sessionRepository.generateMissingSnapshots()
            dataStore.edit { it[ROUTE_SNAPSHOT_BACKFILL_V1_DONE] = true }
        }
    }
}
