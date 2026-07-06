package dev.melcodes.kilometre.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.melcodes.kilometre.domain.models.Accompagnateur
import kotlinx.coroutines.flow.Flow

// DAO for the Accompagnateur entity. v0.1 typically has one row
// (typically a parent) but the schema supports many; onboarding
// inserts the first one and Phase 5+ adds a manage-accompagnateurs
// screen if a second ever becomes useful.
//
// Note: ARCHITECTURE.md's file tree omitted this DAO; it's added
// here because the Accompagnateur entity is unusable without insert
// and read accessors. Codemap entry covers the addition.
@Dao
interface AccompagnateurDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(accompagnateur: Accompagnateur): Long

    // In-place edit, used by onboarding to turn the pre-existing
    // placeholder accompagnateur into the real one without inserting a
    // duplicate (sessions reference accompagnateur.id by foreign key, so
    // the row must keep its id).
    @Update
    suspend fun update(accompagnateur: Accompagnateur)

    @Query("SELECT * FROM accompagnateur WHERE driverId = :driverId ORDER BY id ASC")
    fun observeForDriver(driverId: Long): Flow<List<Accompagnateur>>

    @Query("SELECT * FROM accompagnateur WHERE id = :id")
    suspend fun getById(id: Long): Accompagnateur?

    @Query("SELECT * FROM accompagnateur WHERE driverId = :driverId ORDER BY id ASC LIMIT 1")
    suspend fun firstForDriver(driverId: Long): Accompagnateur?

    @Delete
    suspend fun delete(accompagnateur: Accompagnateur)
}
