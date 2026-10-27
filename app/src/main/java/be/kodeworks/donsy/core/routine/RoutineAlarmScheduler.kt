// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/** One alarm for the next routine reminder; [RoutineReminders] sets the following one when it fires. */
interface RoutineAlarmScheduler {
    /** Replaces any earlier alarm. [at] is wall-clock time: "8:00" follows clock and time zone changes. */
    fun schedule(at: Instant)

    fun cancel()
}

class AndroidRoutineAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: ReminderPermissions,
) : RoutineAlarmScheduler {

    private val alarmManager: AlarmManager = checkNotNull(context.getSystemService())

    override fun schedule(at: Instant) {
        if (permissions.alarmsAllowed()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent())
        } else {
            // The permission was withdrawn after the routine was made: better late than never.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent())
        }
    }

    override fun cancel() = alarmManager.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RoutineAlarmReceiver::class.java).setAction(RoutineAlarmReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
