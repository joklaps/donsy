// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.model.RepeatDays
import java.time.LocalTime

/** When a routine comes back: "Every day at 8:00", "Weekdays at 19:00", "Mon and Thu at 16:30". */
@Composable
fun repeatText(days: RepeatDays, time: LocalTime): String {
    val at = DateFormats.time(time)
    return when {
        days.isEveryDay -> stringResource(R.string.repeat_every_day_at, at)
        days.isWeekdays -> stringResource(R.string.repeat_weekdays_at, at)
        else -> {
            val names = DateFormats.weekInLocaleOrder().filter { it in days }.map { DateFormats.shortWeekday(it) }
            stringResource(R.string.repeat_days_at, DateFormats.list(names), at)
        }
    }
}
