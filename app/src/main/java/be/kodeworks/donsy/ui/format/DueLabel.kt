// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.kodeworks.donsy.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Neutral wording for a due date and its optional [time]. Past dates read "Was due Tue": never
 * "overdue", never alarming (see docs/spec.md, "Task lifecycle"). Without a time a task is due by
 * the end of its day, so it only becomes past the next day.
 */
sealed interface DueLabel {
    val time: LocalTime?

    data class Today(override val time: LocalTime? = null) : DueLabel
    data class Tomorrow(override val time: LocalTime? = null) : DueLabel

    /** Two to six days ahead: shown as a weekday. */
    data class ThisWeek(val date: LocalDate, override val time: LocalTime? = null) : DueLabel
    data class Later(val date: LocalDate, val isThisYear: Boolean, override val time: LocalTime? = null) : DueLabel

    /** Today, but the chosen time has passed. */
    data class EarlierToday(override val time: LocalTime) : DueLabel

    /** Before today; [withinWeek] dates are shown as a weekday. */
    data class Past(
        val date: LocalDate,
        val withinWeek: Boolean,
        val isThisYear: Boolean,
        override val time: LocalTime? = null,
    ) : DueLabel

    val isPast: Boolean get() = this is Past || this is EarlierToday

    companion object {
        fun of(due: LocalDate, time: LocalTime?, now: LocalDateTime): DueLabel {
            val today = now.toLocalDate()
            return when {
                due == today && time != null && time.isBefore(now.toLocalTime()) -> EarlierToday(time)
                due == today -> Today(time)
                due == today.plusDays(1) -> Tomorrow(time)
                due.isAfter(today) && due.isBefore(today.plusDays(7)) -> ThisWeek(due, time)
                due.isAfter(today) -> Later(due, due.year == today.year, time)
                else -> Past(due, withinWeek = due.isAfter(today.minusDays(7)), isThisYear = due.year == today.year, time)
            }
        }
    }
}

@Composable
fun dueLabelText(label: DueLabel): String {
    val time = label.time?.let { DateFormats.time(it) }
    return when (label) {
        is DueLabel.Today -> if (time == null) stringResource(R.string.due_today) else stringResource(R.string.due_today_at, time)
        is DueLabel.Tomorrow ->
            if (time == null) stringResource(R.string.due_tomorrow) else stringResource(R.string.due_tomorrow_at, time)
        is DueLabel.ThisWeek -> dueOn(DateFormats.weekday(label.date), time)
        is DueLabel.Later -> dueOn(DateFormats.shortDate(label.date, label.isThisYear), time)
        is DueLabel.EarlierToday -> stringResource(R.string.was_due_today_at, time!!)
        is DueLabel.Past -> {
            val date = if (label.withinWeek) DateFormats.weekday(label.date) else DateFormats.shortDate(label.date, label.isThisYear)
            if (time == null) stringResource(R.string.was_due_on, date) else stringResource(R.string.was_due_on_at, date, time)
        }
    }
}

@Composable
private fun dueOn(date: String, time: String?): String =
    if (time == null) stringResource(R.string.due_on, date) else stringResource(R.string.due_on_at, date, time)
