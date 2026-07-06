package dev.melcodes.kilometre.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.melcodes.kilometre.domain.models.EditLog
import kotlinx.coroutines.flow.Flow

// DAO for the append-only EditLog. Phase 1 creates the table but
// nothing inserts into it yet — the UI cannot edit a SIGNED session
// because the SIGNED state itself does not yet exist. Phase 3 wires
// edits to insert rows here.
@Dao
interface EditLogDao {

    @Insert
    suspend fun insert(entry: EditLog): Long

    @Query("SELECT * FROM edit_log WHERE sessionId = :sessionId ORDER BY changedAt ASC")
    fun observeForSession(sessionId: Long): Flow<List<EditLog>>

    @Query("SELECT COUNT(*) FROM edit_log WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int
}
