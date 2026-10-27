// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.focus

import be.kodeworks.donsy.domain.model.FocusSessionStatus
import java.time.Duration
import java.time.Instant

/**
 * A focus session, stored as timestamps rather than a ticking counter so it can be recovered
 * after process death, a reboot or a clock change. See [FocusSessionMachine].
 */
data class FocusSession(
    val id: Long = 0,
    val taskId: Long?,
    val subtaskId: Long?,
    val planned: Duration,
    val status: FocusSessionStatus,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    /** Run time of all finished run segments. */
    val accumulatedRun: Duration = Duration.ZERO,
    /** Start of the current run segment; null while paused or ended. */
    val lastResumedAtWall: Instant? = null,
    val lastResumedAtElapsedMillis: Long? = null,
    /** Boot in which [lastResumedAtElapsedMillis] was taken. */
    val bootCount: Int,
    val pausedAt: Instant? = null,
) {
    val isActive: Boolean get() = status == FocusSessionStatus.RUNNING || status == FocusSessionStatus.PAUSED
}

/** Why a session was ended by recovery rather than by the user. */
enum class RecoveryNote {
    /** The time ran out while the app wasn't open. */
    FINISHED_WHILE_AWAY,

    /** It sat paused for longer than [FocusSessionMachine.LONG_PAUSE]. */
    ENDED_AFTER_LONG_PAUSE,
}
