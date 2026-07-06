package dev.melcodes.kilometre.data.db

import androidx.room.TypeConverter
import dev.melcodes.kilometre.domain.models.DrivingScheme
import dev.melcodes.kilometre.domain.models.SessionState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

// Room only knows how to persist primitives and Strings out of the box.
// Every other type the entities reference — kotlinx-datetime's Instant
// and LocalDate, and our enums — needs a pair of @TypeConverter methods
// telling Room how to flatten on write and reinflate on read.
//
// Storage choices:
//   - Instant -> Long (epoch milliseconds). Compact, sortable, and
//     trivial to filter/range-query in SQL.
//   - LocalDate -> String (ISO YYYY-MM-DD). Human-readable when you
//     poke at the DB directly during dev, and the value lacks a
//     timezone anchor so Long-based epoch days would be confusing.
//   - Enum -> String (its `name`). String form survives renames if
//     we ever need to migrate; ordinal form does not.
class Converters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun drivingSchemeToString(value: DrivingScheme): String = value.name

    @TypeConverter
    fun stringToDrivingScheme(value: String): DrivingScheme = DrivingScheme.valueOf(value)

    @TypeConverter
    fun sessionStateToString(value: SessionState): String = value.name

    @TypeConverter
    fun stringToSessionState(value: String): SessionState = SessionState.valueOf(value)
}
