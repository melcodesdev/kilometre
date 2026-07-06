package dev.melcodes.kilometre.domain.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

// Append-only audit log of edits made to SIGNED sessions. Phase 1
// creates the table but does not write to it — the UI cannot yet
// edit a session. Phase 3 introduces the signing flow, and edits
// to signed sessions start producing rows here, one per field
// change.
@Entity(
    tableName = "edit_log",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
        ),
    ],
    indices = [Index("sessionId"), Index("changedAt")],
)
data class EditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val fieldChanged: String,
    val oldValue: String,
    val newValue: String,
    val changedAt: Instant,
    val reason: String,
)
