// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Instants are stored as epoch milliseconds, dates as epoch days, times as minutes since midnight.
 * Enums use Room's by-name mapping.
 */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localTimeToMinuteOfDay(value: LocalTime?): Int? = value?.let { it.hour * 60 + it.minute }

    @TypeConverter
    fun minuteOfDayToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.of(it / 60, it % 60) }
}
