package dev.melcodes.kilometre.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.melcodes.kilometre.domain.models.Driver
import kotlinx.coroutines.flow.Flow

// Singleton-row DAO for the Driver entity. v0.1 enforces single-driver
// by always inserting with id = 1 and REPLACE-on-conflict.
//
// Idiom notes for new-to-Kotlin readers:
//   - `suspend` means "this function can pause without blocking the
//     thread it's called on." Room generates an implementation that
//     hops onto Dispatchers.IO under the hood. We never call this
//     from the UI thread directly; we call it from a coroutine.
//   - `Flow<T?>` is a stream of values over time. Room emits a new
//     value whenever the underlying row changes. The UI collects
//     the flow and re-renders.
@Dao
interface DriverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(driver: Driver)

    // Non-destructive update for onboarding on an install that already
    // has a Driver row. We avoid REPLACE-upsert here: REPLACE does a
    // DELETE-then-INSERT, and deleting driver id = 1 while sessions and
    // accompagnateurs reference it would violate the foreign keys. UPDATE
    // edits the row in place, so existing sessions stay linked.
    @Update
    suspend fun update(driver: Driver)

    @Query("SELECT * FROM driver WHERE id = 1")
    fun observeDriver(): Flow<Driver?>

    @Query("SELECT * FROM driver WHERE id = 1")
    suspend fun getDriver(): Driver?

    @Query("SELECT COUNT(*) FROM driver")
    suspend fun count(): Int
}
