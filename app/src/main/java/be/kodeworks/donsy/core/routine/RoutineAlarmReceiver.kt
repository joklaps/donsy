// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.routine

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.kodeworks.donsy.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The routine alarm and a notification's "Done" action. */
@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: RoutineReminders

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val pending = goAsync()
        applicationScope.launch {
            try {
                if (intent.action == ACTION_DONE && taskId >= 0) reminders.markDone(taskId) else reminders.sync()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "be.kodeworks.donsy.action.ROUTINE_REMIND"
        const val ACTION_DONE = "be.kodeworks.donsy.action.ROUTINE_DONE"
        const val EXTRA_TASK_ID = "be.kodeworks.donsy.extra.TASK_ID"
    }
}

/**
 * Alarms are lost on reboot and must follow clock and time zone changes; the exact-alarm
 * permission can also come back. Each of these re-runs [RoutineReminders.sync].
 */
@AndroidEntryPoint
class RoutineRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: RoutineReminders

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        applicationScope.launch {
            try {
                reminders.sync()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // The exact-alarm action is a plain string: on Android 10–11 it is simply never broadcast.
        @SuppressLint("InlinedApi")
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
