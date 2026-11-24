package com.zmr.rent.data

import androidx.room.TypeConverter
import java.time.LocalDate

/** How Room stores types it does not know. A calendar date is stored as its epoch day number. */
class Converters {

    // Enum columns are handled by Room; this class only covers dates.
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
