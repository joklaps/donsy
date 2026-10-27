// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.recommendation

import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.model.Subtask
import be.kodeworks.donsy.domain.model.Task

/** What the user told Donsy about right now. Everything is optional. */
data class RecommendationContext(
    val availableMinutes: Int? = null,
    val energy: EnergyLevel? = null,
    /** Tasks passed over with "Show another" during this visit. */
    val skippedTaskIds: Set<Long> = emptySet(),
    /** Unprocessed BrainDump items, used to pick starter ideas. */
    val brainDumpCount: Int = 0,
)

sealed interface Recommendation {

    /** One suggestion. [step] is the first open step when the task has one. */
    data class Suggest(
        val task: Task,
        val step: Subtask?,
        val score: Int,
        /** One or two reasons, most important first. */
        val reasons: List<Reason>,
    ) : Recommendation

    /** Nothing eligible; offer [starters] instead. */
    data class Nothing(val starters: List<Starter>, val allSkipped: Boolean) : Recommendation
}

/** Why something was suggested. Language-neutral; the app turns these into sentences. */
sealed interface Reason {
    data object DueToday : Reason
    data object DueTomorrow : Reason
    data class DueSoon(val days: Int) : Reason

    /** Past its date. Worded gently: a small step still counts. */
    data object WasDue : Reason
    data object Important : Reason

    /** The (step's share of the) estimate fits in the time the user has. */
    data class FitsAvailableTime(val availableMinutes: Int) : Reason

    /** A short task when no available time was given. */
    data class Quick(val estimateMinutes: Int) : Reason
    data class MatchesEnergy(val energy: EnergyLevel) : Reason
    data object WorkedOnRecently : Reason
    data class StepsInProgress(val done: Int, val total: Int) : Reason

    /** Every step is checked off; only the task itself is left. */
    data object AllStepsDone : Reason

    /** Fallback when nothing stands out. */
    data object GoodPlaceToStart : Reason
}

/** Tiny ways to get going when there is nothing to suggest. */
enum class Starter { SORT_BRAIN_DUMP, ADD_SMALL_TASK, PICK_ANY_TASK }
