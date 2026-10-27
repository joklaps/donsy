// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.focus

import be.kodeworks.donsy.domain.focus.RecoveryNote
import be.kodeworks.donsy.domain.model.FocusSessionStatus

/** What a session is about: a task, optionally one of its steps. */
data class FocusTarget(val taskId: Long, val subtaskId: Long? = null)

/** Shown after a session ends (not after a cancel). */
data class FocusSummary(
    val sessionId: Long,
    val target: FocusTarget?,
    val status: FocusSessionStatus,
    val focusedMinutes: Int,
    /** Set when recovery, not the user, ended the session. */
    val note: RecoveryNote?,
)
