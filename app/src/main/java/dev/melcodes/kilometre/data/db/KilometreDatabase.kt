package dev.melcodes.kilometre.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.melcodes.kilometre.domain.models.Accompagnateur
import dev.melcodes.kilometre.domain.models.Driver
import dev.melcodes.kilometre.domain.models.EditLog
import dev.melcodes.kilometre.domain.models.GpsPoint
import dev.melcodes.kilometre.domain.models.Session

// The Room database for Kilomètre. Schema version 1 ships with Phase 1.
//
// Schema version policy:
//   - Additive changes (new columns with defaults, new tables, new
//     indices) use Room auto-migrations. The version number bumps,
//     `@AutoMigration` is added below, but no migration code is
//     hand-written.
//   - Destructive changes (column type changes, renames, splits) need
//     manual Migration objects. Add them via .addMigrations(...) in
//     the Room.databaseBuilder call when they arrive.
//   - We never fall back to destructive recreation in production. If
//     migration code is missing, the build fails — that is the desired
//     behaviour because losing the user's driving log to a forgotten
//     migration would be catastrophic.
//
// `exportSchema = true` writes the schema JSON into app/schemas/ at
// build time. That directory is committed so schema diffs show up in
// code review when a column or index changes.
@Database(
    entities = [
        Driver::class,
        Accompagnateur::class,
        Session::class,
        GpsPoint::class,
        EditLog::class,
    ],
    version = 2,
    exportSchema = true,
    // v1 → v2 adds the nullable Session.manualPauseStartedAt column for the
    // manual pause/resume feature. A new nullable column is a purely additive
    // change, so Room generates the ALTER TABLE itself — no hand-written
    // migration, no data touched.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class KilometreDatabase : RoomDatabase() {

    abstract fun driverDao(): DriverDao
    abstract fun accompagnateurDao(): AccompagnateurDao
    abstract fun sessionDao(): SessionDao
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun editLogDao(): EditLogDao

    companion object {
        private const val DB_NAME = "kilometre.db"

        // Build the singleton database instance. Called once from
        // AppContainer in Phase 1 commit 5. The volatile + double-
        // checked locking pattern is unnecessary because AppContainer
        // itself is the single source of truth, but the helper here
        // keeps the construction logic adjacent to the schema.
        fun build(context: Context): KilometreDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KilometreDatabase::class.java,
                DB_NAME,
            )
                // Phase 1 only ships version 1. When the first
                // schema change lands we add auto-migrations or
                // manual Migration objects here.
                .build()
    }
}
