// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.core.routine.ReminderPermissions
import be.kodeworks.donsy.core.routine.RoutineAlarmScheduler
import be.kodeworks.donsy.core.routine.RoutineNotifier
import be.kodeworks.donsy.domain.model.Task
import java.time.Instant

class FakeReminderPermissions(var missing: MissingReminderPermission? = null) : ReminderPermissions {
    override fun missing(): MissingReminderPermission? = missing

    override fun alarmsAllowed(): Boolean = missing != MissingReminderPermission.ALARMS
}

class FakeRoutineAlarmScheduler : RoutineAlarmScheduler {
    /** The pending alarm; null when cancelled. */
    var scheduled: Instant? = null

    override fun schedule(at: Instant) {
        scheduled = at
    }

    override fun cancel() {
        scheduled = null
    }
}

class FakeRoutineNotifier : RoutineNotifier {
    /** Task ids of the notifications on screen. */
    val showing = mutableSetOf<Long>()

    /** Every reminder shown, in order. */
    val shown = mutableListOf<Long>()

    override fun show(task: Task) {
        showing += task.id
        shown += task.id
    }

    override fun retainOnly(taskIds: Set<Long>) {
        showing.retainAll(taskIds)
    }
}
