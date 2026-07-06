package dev.melcodes.kilometre.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

// The single driver this app tracks. v0.1 is single-driver; the id is
// pinned to 1L and the onboarding code does INSERT OR REPLACE so a
// second row can never exist. The `id = 1L` default plus a fixed
// primary key is how we express "singleton" in Room without an
// extra constraint table.
//
// `birthdate` is a placeholder in Phase 1 — the onboarding prompt for it was dropped, and Phase 6 adds
// it back when exports need the "minor when starting AAC" disclaimer.
//
// `metadata` is a JSON string column. Empty `{}` until a feature
// writes structured data into it. Storing JSON in a TEXT column lets
// the schema evolve without migrations for additive fields.
@Entity(tableName = "driver")
data class Driver(
    @PrimaryKey val id: Long = 1L,
    val name: String,
    val birthdate: LocalDate,
    val scheme: DrivingScheme,
    val startDate: LocalDate,
    val kmGoal: Int,
    val createdAt: Instant,
    val metadata: String = "{}",
)
