// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.focus

import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.focus.FocusSession
import be.kodeworks.donsy.domain.focus.FocusSessionMachine
import be.kodeworks.donsy.domain.focus.RecoveryNote
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.repository.FocusRepository
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs focus sessions: applies [FocusSessionMachine] transitions, stores them, and keeps the
 * notification and end alarm in step. Ending a session never changes the task's status.
 */
@Singleton
class FocusSessionManager @Inject constructor(
    private val sessions: FocusRepository,
    private val tasks: TaskRepository,
    private val time: TimeSource,
    private val alarms: FocusAlarmScheduler,
    private val notifier: FocusNotifier,
    private val preferences: UserPreferencesRepository,
) {
    private val mutex = Mutex()

    val activeSession: Flow<FocusSession?> = sessions.observeActive()

    private val summaryState = MutableStateFlow<FocusSummary?>(null)

    /** The most recently ended session, until the user closes its summary. */
    val summary: StateFlow<FocusSummary?> = summaryState.asStateFlow()

    private val pendingTargetState = MutableStateFlow<FocusTarget?>(null)

    /** A target handed over from another screen (Home's "Start"), picked up by the Focus screen. */
    val pendingTarget: StateFlow<FocusTarget?> = pendingTargetState.asStateFlow()

    /** Hands [target] to the Focus screen. Choosing something new also closes a previous session's summary. */
    fun prepare(target: FocusTarget) {
        summaryState.value = null
        pendingTargetState.value = target
    }

    fun consumePendingTarget() {
        pendingTargetState.value = null
    }

    fun dismissSummary() {
        summaryState.value = null
    }

    /** Starts a session unless one is already active (there is at most one). */
    suspend fun start(target: FocusTarget?, minutes: Int) = mutex.withLock {
        if (sessions.getActive() != null) return@withLock
        val session = FocusSessionMachine.start(target?.taskId, target?.subtaskId, Duration.ofMinutes(minutes.toLong()), time)
        val stored = session.copy(id = sessions.insert(session))
        summaryState.value = null
        onRunning(stored)
    }

    suspend fun pause() = transition { session ->
        FocusSessionMachine.pause(session, time).also { paused ->
            alarms.cancel()
            notifier.showPaused(targetTitle(paused), minutesLeft(paused))
        }
    }

    suspend fun resume() = transition { session ->
        FocusSessionMachine.resume(session, time).also { onRunning(it) }
    }

    /** "Finish now": ends the session and counts the time as work on the task. */
    suspend fun finish() = transition { session ->
        FocusSessionMachine.finish(session, time).also { onEnded(it, note = null) }
    }

    /** Ends the session without counting it. */
    suspend fun cancel() = transition { session ->
        FocusSessionMachine.cancel(session, time).also { onEnded(it, note = null) }
    }

    /**
     * Brings the active session up to date: on app start, when the Focus screen returns, when the
     * end alarm fires, and when the on-screen timer reaches zero ([userPresent]: no "while you were
     * away" note then). A still-running session gets its alarm and notification back, e.g. after
     * a reboot cleared them.
     */
    suspend fun recover(userPresent: Boolean) = mutex.withLock {
        val session = sessions.getActive() ?: return@withLock
        val (recovered, note) = FocusSessionMachine.recover(session, time)
        when {
            recovered != session -> {
                sessions.update(recovered)
                onEnded(recovered, note.takeUnless { userPresent && it == RecoveryNote.FINISHED_WHILE_AWAY })
                if (recovered.status == FocusSessionStatus.COMPLETED) notifier.showCompleted()
            }
            session.status == FocusSessionStatus.RUNNING -> onRunning(session)
        }
    }

    private suspend fun transition(change: suspend (FocusSession) -> FocusSession) = mutex.withLock {
        val session = sessions.getActive() ?: return@withLock
        val changed = change(session)
        if (changed != session) sessions.update(changed)
    }

    private suspend fun onRunning(session: FocusSession) {
        val remaining = FocusSessionMachine.remaining(session, time)
        alarms.schedule(time.elapsedRealtimeMillis() + remaining.toMillis(), precise = preferences.preciseEndAlerts.first())
        notifier.showRunning(targetTitle(session), endsAt = time.now().plus(remaining))
    }

    private suspend fun onEnded(session: FocusSession, note: RecoveryNote?) {
        alarms.cancel()
        notifier.cancelOngoing()
        if (session.status == FocusSessionStatus.CANCELLED) return
        val taskId = session.taskId
        if (taskId != null && session.accumulatedRun >= MIN_WORK) tasks.recordWork(taskId)
        summaryState.value = FocusSummary(
            sessionId = session.id,
            target = taskId?.let { FocusTarget(it, session.subtaskId) },
            status = session.status,
            focusedMinutes = roundedMinutes(session.accumulatedRun),
            note = note,
        )
    }

    private suspend fun targetTitle(session: FocusSession): String? {
        val task = session.taskId?.let { tasks.observeTask(it).first() } ?: return null
        return task.subtasks.firstOrNull { it.id == session.subtaskId }?.title ?: task.title
    }

    private fun minutesLeft(session: FocusSession): Int =
        roundedUpMinutes(FocusSessionMachine.remaining(session, time))

    companion object {
        /** Shorter sessions don't count as having worked on the task. */
        val MIN_WORK: Duration = Duration.ofMinutes(1)

        fun roundedMinutes(duration: Duration): Int =
            ((duration.seconds + 30) / 60).toInt().let { if (it == 0 && !duration.isZero) 1 else it }

        fun roundedUpMinutes(duration: Duration): Int = ((duration.seconds + 59) / 60).toInt()
    }
}
