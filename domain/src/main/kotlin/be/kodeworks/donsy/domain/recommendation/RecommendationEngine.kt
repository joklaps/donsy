// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.recommendation

import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Picks one next step. Rule-based, offline and deterministic: integer scores only, and a full
 * tie-break order, so the same tasks and context always give the same suggestion.
 * The rules are specified in docs/spec.md, "Next-action recommendation".
 */
object RecommendationEngine {

    const val URGENCY_WEIGHT = 30
    const val IMPORTANCE_WEIGHT = 25
    const val TIME_FIT_WEIGHT = 20
    const val ENERGY_FIT_WEIGHT = 15
    const val CONTINUITY_WEIGHT = 10

    private const val QUICK_MINUTES = 15
    private val RECENT_WORK = Duration.ofHours(24)

    fun recommend(tasks: List<Task>, context: RecommendationContext, now: Instant, zone: ZoneId): Recommendation {
        val today = now.atZone(zone).toLocalDate()
        val eligible = tasks.filter { it.isEligible(now) }
        val candidates = eligible.filter { it.id !in context.skippedTaskIds }
        if (candidates.isEmpty()) {
            return Recommendation.Nothing(
                starters = starters(tasks, eligible, context),
                allSkipped = eligible.isNotEmpty(),
            )
        }
        val best = candidates
            .map { Scored(it, scores(it, context, now, today)) }
            .sortedWith(bestFirst)
            .first()
        return Recommendation.Suggest(
            task = best.task,
            step = best.task.subtasks.firstOrNull { !it.isDone },
            score = best.scores.total,
            reasons = reasons(best.task, best.scores, context, today),
        )
    }

    /** "Not now" hides a task until the start of the next local day. */
    fun notNowUntil(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    /** Routines are never suggested: they have their own reminder and aren't work to start on. */
    private fun Task.isEligible(now: Instant): Boolean =
        status == TaskStatus.ACTIVE && !isRoutine && (notNowUntil == null || !notNowUntil.isAfter(now))

    internal data class Scores(
        val urgency: Int,
        val importance: Int,
        val timeFit: Int,
        val energyFit: Int,
        val continuity: Int,
    ) {
        val total: Int
            get() = URGENCY_WEIGHT * urgency + IMPORTANCE_WEIGHT * importance + TIME_FIT_WEIGHT * timeFit +
                ENERGY_FIT_WEIGHT * energyFit + CONTINUITY_WEIGHT * continuity
    }

    private data class Scored(val task: Task, val scores: Scores)

    /**
     * Highest score; then most urgent; then earliest due date and time (no time = end of that day);
     * then oldest; then lowest id.
     */
    private val bestFirst = compareByDescending<Scored> { it.scores.total }
        .thenByDescending { it.scores.urgency }
        .thenBy(nullsLast()) { it.task.dueDate }
        .thenBy { it.task.dueTime ?: LocalTime.MAX }
        .thenBy { it.task.createdAt }
        .thenBy { it.task.id }

    internal fun scores(task: Task, context: RecommendationContext, now: Instant, today: LocalDate) = Scores(
        urgency = urgency(task.dueDate, today),
        importance = if (task.importance == Importance.HIGH) 100 else 40,
        timeFit = timeFit(effectiveEstimate(task), context.availableMinutes),
        energyFit = energyFit(context.energy, task.effort),
        continuity = continuity(task, now),
    )

    internal fun urgency(due: LocalDate?, today: LocalDate): Int {
        if (due == null) return 0
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days in 0..1 -> 100
            days in 2..3 -> 80
            days in 4..7 -> 50
            days > 7 -> 20
            // Past due: full weight for three days, then fading to 40 so it can't dominate forever.
            -days <= 3 -> 100
            else -> maxOf(40, 100 - ((-days - 3) * 10).toInt())
        }
    }

    /**
     * The time the next piece of work takes: a task's estimate shared over its open steps, since
     * only one step is suggested. Rounded up.
     */
    fun effectiveEstimate(task: Task): Int? {
        val estimate = task.estimateMinutes ?: return null
        val openSteps = task.openStepCount.coerceAtLeast(1)
        return (estimate + openSteps - 1) / openSteps
    }

    internal fun timeFit(estimate: Int?, available: Int?): Int = when {
        available == null -> 50
        estimate == null -> 60
        estimate <= available -> 100
        else -> 20
    }

    internal fun energyFit(energy: EnergyLevel?, effort: Effort?): Int {
        if (energy == null) return 50
        if (effort == null) return 60
        return when (energy) {
            EnergyLevel.LOW -> when (effort) { Effort.LOW -> 100; Effort.MEDIUM -> 50; Effort.HIGH -> 10 }
            EnergyLevel.MEDIUM -> when (effort) { Effort.LOW -> 80; Effort.MEDIUM -> 100; Effort.HIGH -> 50 }
            EnergyLevel.HIGH -> when (effort) { Effort.LOW -> 60; Effort.MEDIUM -> 80; Effort.HIGH -> 100 }
        }
    }

    internal fun continuity(task: Task, now: Instant): Int {
        val worked = task.lastWorkedAt
        return when {
            worked != null && Duration.between(worked, now) <= RECENT_WORK -> 100
            task.doneStepCount > 0 -> 70
            else -> 0
        }
    }

    /** The one or two strongest signals, in plain terms. Neutral defaults are never reasons. */
    private fun reasons(task: Task, scores: Scores, context: RecommendationContext, today: LocalDate): List<Reason> {
        if (task.subtasks.isNotEmpty() && task.openStepCount == 0) return listOf(Reason.AllStepsDone)

        val candidates = mutableListOf<Pair<Reason, Int>>()
        task.dueDate?.let { due ->
            val days = ChronoUnit.DAYS.between(today, due).toInt()
            val reason = when {
                days < 0 -> Reason.WasDue
                days == 0 -> Reason.DueToday
                days == 1 -> Reason.DueTomorrow
                days <= 7 -> Reason.DueSoon(days)
                else -> null
            }
            reason?.let { candidates += it to URGENCY_WEIGHT * scores.urgency }
        }
        if (task.importance == Importance.HIGH) candidates += Reason.Important to IMPORTANCE_WEIGHT * scores.importance
        val estimate = effectiveEstimate(task)
        when {
            context.availableMinutes != null && scores.timeFit == 100 ->
                candidates += Reason.FitsAvailableTime(context.availableMinutes) to TIME_FIT_WEIGHT * scores.timeFit
            context.availableMinutes == null && estimate != null && estimate <= QUICK_MINUTES ->
                candidates += Reason.Quick(estimate) to TIME_FIT_WEIGHT * 60
        }
        if (context.energy != null && scores.energyFit == 100) {
            candidates += Reason.MatchesEnergy(context.energy) to ENERGY_FIT_WEIGHT * scores.energyFit
        }
        when (scores.continuity) {
            100 -> candidates += Reason.WorkedOnRecently to CONTINUITY_WEIGHT * 100
            70 -> candidates += Reason.StepsInProgress(task.doneStepCount, task.subtasks.size) to CONTINUITY_WEIGHT * 70
        }
        // Stable sort: equal contributions keep the order above.
        return candidates.sortedByDescending { it.second }.take(2).map { it.first }
            .ifEmpty { listOf(Reason.GoodPlaceToStart) }
    }

    private fun starters(tasks: List<Task>, eligible: List<Task>, context: RecommendationContext): List<Starter> =
        buildList {
            if (context.brainDumpCount > 0) add(Starter.SORT_BRAIN_DUMP)
            add(Starter.ADD_SMALL_TASK)
            if (eligible.isNotEmpty() || tasks.any { it.status == TaskStatus.ACTIVE || it.status == TaskStatus.PAUSED }) {
                add(Starter.PICK_ANY_TASK)
            }
        }
}
