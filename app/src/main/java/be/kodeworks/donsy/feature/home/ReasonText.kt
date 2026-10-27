// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.recommendation.Reason
import be.kodeworks.donsy.domain.recommendation.Starter

/** One full, translatable sentence per reason code. */
@Composable
fun reasonText(reason: Reason): String = when (reason) {
    Reason.DueToday -> stringResource(R.string.reason_due_today)
    Reason.DueTomorrow -> stringResource(R.string.reason_due_tomorrow)
    is Reason.DueSoon -> pluralStringResource(R.plurals.reason_due_soon, reason.days, reason.days)
    Reason.WasDue -> stringResource(R.string.reason_was_due)
    Reason.Important -> stringResource(R.string.reason_important)
    is Reason.FitsAvailableTime ->
        pluralStringResource(R.plurals.reason_fits_time, reason.availableMinutes, reason.availableMinutes)
    is Reason.Quick -> pluralStringResource(R.plurals.reason_quick, reason.estimateMinutes, reason.estimateMinutes)
    is Reason.MatchesEnergy -> stringResource(
        when (reason.energy) {
            EnergyLevel.LOW -> R.string.reason_energy_low
            EnergyLevel.MEDIUM -> R.string.reason_energy_medium
            EnergyLevel.HIGH -> R.string.reason_energy_high
        },
    )
    Reason.WorkedOnRecently -> stringResource(R.string.reason_worked_recently)
    is Reason.StepsInProgress -> pluralStringResource(R.plurals.reason_steps_in_progress, reason.total, reason.done, reason.total)
    Reason.AllStepsDone -> stringResource(R.string.reason_all_steps_done)
    Reason.GoodPlaceToStart -> stringResource(R.string.reason_good_start)
}

@Composable
fun starterText(starter: Starter): String = stringResource(
    when (starter) {
        Starter.SORT_BRAIN_DUMP -> R.string.starter_sort_braindump
        Starter.ADD_SMALL_TASK -> R.string.starter_add_small_task
        Starter.PICK_ANY_TASK -> R.string.starter_pick_any_task
    },
)

/** The energy check-in choice on Home. */
@Composable
fun energyText(level: EnergyLevel): String = stringResource(
    when (level) {
        EnergyLevel.LOW -> R.string.energy_low
        EnergyLevel.MEDIUM -> R.string.energy_medium
        EnergyLevel.HIGH -> R.string.energy_high
    },
)
