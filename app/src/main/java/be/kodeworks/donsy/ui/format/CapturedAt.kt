// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.kodeworks.donsy.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** When something was captured, relative to today. Language-neutral; see [capturedAtLabel]. */
data class CapturedAt(val day: Day, val time: LocalTime) {

    sealed interface Day {
        data object Today : Day
        data object Yesterday : Day
        data class Earlier(val date: LocalDate, val isThisYear: Boolean) : Day
    }

    companion object {
        fun of(instant: Instant, now: Instant, zone: ZoneId): CapturedAt {
            val captured = instant.atZone(zone)
            val today = now.atZone(zone).toLocalDate()
            val day = when (captured.toLocalDate()) {
                today -> Day.Today
                today.minusDays(1) -> Day.Yesterday
                else -> Day.Earlier(captured.toLocalDate(), isThisYear = captured.year == today.year)
            }
            return CapturedAt(day, captured.toLocalTime())
        }
    }
}

/** Localized label such as "Today, 14:32" that respects the user's 12/24-hour setting. */
@Composable
fun capturedAtLabel(capturedAt: CapturedAt): String {
    val time = DateFormats.time(capturedAt.time)
    return when (val day = capturedAt.day) {
        CapturedAt.Day.Today -> stringResource(R.string.captured_today, time)
        CapturedAt.Day.Yesterday -> stringResource(R.string.captured_yesterday, time)
        is CapturedAt.Day.Earlier ->
            stringResource(R.string.captured_on_date, DateFormats.shortDate(day.date, day.isThisYear), time)
    }
}
