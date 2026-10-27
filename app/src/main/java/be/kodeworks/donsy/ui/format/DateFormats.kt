// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.format

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import android.text.format.DateFormat as AndroidDateFormat

/**
 * Locale-aware date and time formatting from skeletons, so each language gets its own
 * word order and separators. Never build date strings by hand (see docs/i18n.md).
 */
object DateFormats {

    @Composable
    fun time(time: LocalTime): String {
        val skeleton = if (AndroidDateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hma"
        return format(skeleton) { time.format(it) }
    }

    /** "3 Oct", or "3 Oct 2025" for another year. */
    @Composable
    fun shortDate(date: LocalDate, isThisYear: Boolean): String =
        format(if (isThisYear) "dMMM" else "dMMMy") { date.format(it) }

    /** Full weekday name, e.g. "Friday". */
    @Composable
    fun weekday(date: LocalDate): String = format("EEEE") { date.format(it) }

    /** Short day name, e.g. "Mon" or "ma". */
    @Composable
    fun shortWeekday(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])

    /** The week as people in this locale see it: Monday first in Belgium, Sunday first in the US. */
    @Composable
    fun weekInLocaleOrder(): List<DayOfWeek> {
        val first = WeekFields.of(LocalConfiguration.current.locales[0]).firstDayOfWeek
        return (0L..6L).map { first.plus(it) }
    }

    /** "Mon, Wed and Fri", with the locale's own separators and word for "and". */
    @Composable
    fun list(items: List<String>): String = ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(items)

    @Composable
    private fun format(skeleton: String, block: (DateTimeFormatter) -> String): String {
        val locale = LocalConfiguration.current.locales[0]
        return block(DateTimeFormatter.ofPattern(AndroidDateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }
}
