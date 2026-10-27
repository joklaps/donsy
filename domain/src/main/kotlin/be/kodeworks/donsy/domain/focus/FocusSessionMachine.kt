// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.focus

import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Pure state transitions for focus sessions (docs/spec.md, "Focus sessions").
 *
 * Run time is measured with the monotonic clock while the device stays on, so changing the wall
 * clock has no effect. After a reboot the monotonic clock restarts, so the current segment falls
 * back to wall-clock time, never negative.
 */
object FocusSessionMachine {

    /** A paused session older than this is wrapped up on recovery. */
    val LONG_PAUSE: Duration = Duration.ofHours(12)

    fun start(taskId: Long?, subtaskId: Long?, planned: Duration, time: TimeSource): FocusSession {
        require(!planned.isNegative && !planned.isZero) { "planned must be positive" }
        val now = time.now()
        return FocusSession(
            taskId = taskId,
            subtaskId = subtaskId,
            planned = planned,
            status = FocusSessionStatus.RUNNING,
            startedAt = now,
            lastResumedAtWall = now,
            lastResumedAtElapsedMillis = time.elapsedRealtimeMillis(),
            bootCount = time.bootCount(),
        )
    }

    /** Total time spent running, including the current segment. */
    fun runTime(session: FocusSession, time: TimeSource): Duration = session.accumulatedRun + currentSegment(session, time)

    fun remaining(session: FocusSession, time: TimeSource): Duration =
        (session.planned - runTime(session, time)).coerceAtLeast(Duration.ZERO)

    fun pause(session: FocusSession, time: TimeSource): FocusSession {
        if (session.status != FocusSessionStatus.RUNNING) return session
        return session.copy(
            status = FocusSessionStatus.PAUSED,
            accumulatedRun = runTime(session, time).coerceAtMost(session.planned),
            lastResumedAtWall = null,
            lastResumedAtElapsedMillis = null,
            pausedAt = time.now(),
        )
    }

    fun resume(session: FocusSession, time: TimeSource): FocusSession {
        if (session.status != FocusSessionStatus.PAUSED) return session
        return session.copy(
            status = FocusSessionStatus.RUNNING,
            lastResumedAtWall = time.now(),
            lastResumedAtElapsedMillis = time.elapsedRealtimeMillis(),
            bootCount = time.bootCount(),
            pausedAt = null,
        )
    }

    /** "Finish now": completed if the time is up, otherwise ended early. Never touches the task. */
    fun finish(session: FocusSession, time: TimeSource): FocusSession {
        if (!session.isActive) return session
        val run = runTime(session, time).coerceAtMost(session.planned)
        return ended(session, run, if (run >= session.planned) FocusSessionStatus.COMPLETED else FocusSessionStatus.ENDED_EARLY, time)
    }

    fun cancel(session: FocusSession, time: TimeSource): FocusSession {
        if (!session.isActive) return session
        return ended(session, runTime(session, time).coerceAtMost(session.planned), FocusSessionStatus.CANCELLED, time)
    }

    /**
     * Brings a stored session up to date, e.g. when the app opens. Returns the session unchanged
     * and no note when nothing needs to happen.
     */
    fun recover(session: FocusSession, time: TimeSource): Pair<FocusSession, RecoveryNote?> {
        when (session.status) {
            FocusSessionStatus.RUNNING -> {
                val run = runTime(session, time)
                if (run < session.planned) return session to null
                // Ended when the planned time ran out, not now.
                val overshoot = run - session.planned
                val endedAt = time.now().minus(overshoot).coerceAtLeastInstant(session.startedAt)
                return session.copy(
                    status = FocusSessionStatus.COMPLETED,
                    accumulatedRun = session.planned,
                    lastResumedAtWall = null,
                    lastResumedAtElapsedMillis = null,
                    endedAt = endedAt,
                ) to RecoveryNote.FINISHED_WHILE_AWAY
            }
            FocusSessionStatus.PAUSED -> {
                val pausedAt = session.pausedAt ?: return session to null
                if (Duration.between(pausedAt, time.now()) <= LONG_PAUSE) return session to null
                return session.copy(status = FocusSessionStatus.ENDED_EARLY, endedAt = pausedAt) to
                    RecoveryNote.ENDED_AFTER_LONG_PAUSE
            }
            else -> return session to null
        }
    }

    private fun ended(session: FocusSession, run: Duration, status: FocusSessionStatus, time: TimeSource) = session.copy(
        status = status,
        accumulatedRun = run,
        lastResumedAtWall = null,
        lastResumedAtElapsedMillis = null,
        pausedAt = null,
        endedAt = time.now(),
    )

    private fun currentSegment(session: FocusSession, time: TimeSource): Duration {
        if (session.status != FocusSessionStatus.RUNNING) return Duration.ZERO
        val elapsedStart = session.lastResumedAtElapsedMillis
        val segment = if (elapsedStart != null && session.bootCount == time.bootCount()) {
            Duration.ofMillis(time.elapsedRealtimeMillis() - elapsedStart)
        } else {
            // Rebooted: monotonic time restarted, so wall time is the best we have.
            val wallStart = session.lastResumedAtWall ?: return Duration.ZERO
            Duration.between(wallStart, time.now())
        }
        return segment.coerceAtLeast(Duration.ZERO)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this

    private fun Duration.coerceAtMost(max: Duration) = if (this > max) max else this

    private fun Instant.coerceAtLeastInstant(min: Instant) = if (isBefore(min)) min else this
}
