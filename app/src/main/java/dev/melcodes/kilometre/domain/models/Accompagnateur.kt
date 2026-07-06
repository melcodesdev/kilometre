package dev.melcodes.kilometre.domain.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

// An accompanying adult who can sign a session. v0.1 typically has one
// (typically a parent) but the table supports many, indexed by driver.
//
// `defaultSignaturePath` is reserved for a Phase 3+ feature where a
// previously-drawn signature can be re-used. Null until then.
@Entity(
    tableName = "accompagnateur",
    foreignKeys = [
        ForeignKey(
            entity = Driver::class,
            parentColumns = ["id"],
            childColumns = ["driverId"],
        ),
    ],
    indices = [Index("driverId")],
)
data class Accompagnateur(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverId: Long,
    val name: String,
    val relation: String,
    val defaultSignaturePath: String? = null,
    val createdAt: Instant,
    val metadata: String = "{}",
)
