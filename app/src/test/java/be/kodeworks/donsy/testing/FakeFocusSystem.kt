// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import be.kodeworks.donsy.core.focus.FocusAlarmScheduler
import be.kodeworks.donsy.core.focus.FocusNotifier
import java.time.Instant

class FakeFocusAlarmScheduler(
    var exactAllowed: Boolean = false,
    override val exactNeedsPermission: Boolean = true,
) : FocusAlarmScheduler {
    /** Scheduled trigger on the monotonic clock and whether it was exact; null when cancelled. */
    var scheduled: Pair<Long, Boolean>? = null

    override fun schedule(triggerAtElapsedMillis: Long, precise: Boolean) {
        scheduled = triggerAtElapsedMillis to ((precise || !exactNeedsPermission) && exactAllowed)
    }

    override fun cancel() {
        scheduled = null
    }

    override fun canScheduleExact(): Boolean = exactAllowed
}

class FakeFocusNotifier : FocusNotifier {
    sealed interface Ongoing {
        data class Running(val title: String?, val endsAt: Instant) : Ongoing
        data class Paused(val title: String?, val minutesLeft: Int) : Ongoing
    }

    var ongoing: Ongoing? = null
    var completedCount = 0

    override fun showRunning(targetTitle: String?, endsAt: Instant) {
        ongoing = Ongoing.Running(targetTitle, endsAt)
    }

    override fun showPaused(targetTitle: String?, minutesLeft: Int) {
        ongoing = Ongoing.Paused(targetTitle, minutesLeft)
    }

    override fun cancelOngoing() {
        ongoing = null
    }

    override fun showCompleted() {
        completedCount++
    }
}
