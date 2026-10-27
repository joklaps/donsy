// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Wakes the app when a focus session's time is up, so it can be completed and announced. */
interface FocusAlarmScheduler {
    /**
     * [triggerAtElapsedMillis] is on the monotonic clock, so changing the wall clock doesn't move it.
     * [precise] is the user's choice; it only matters where exact alarms need permission.
     */
    fun schedule(triggerAtElapsedMillis: Long, precise: Boolean)

    fun cancel()

    /** Whether exact alarms are allowed right now (always on Android 10–11). */
    fun canScheduleExact(): Boolean

    /** Android 12+ needs the user's permission for exact alarms; before that they are always used. */
    val exactNeedsPermission: Boolean
}

class AndroidFocusAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : FocusAlarmScheduler {

    private val alarmManager: AlarmManager = checkNotNull(context.getSystemService())

    override fun schedule(triggerAtElapsedMillis: Long, precise: Boolean) {
        val intent = pendingIntent()
        if ((precise || !exactNeedsPermission) && canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedMillis, intent)
        } else {
            // Default: no special permission. Android batches inexact alarms: it may arrive up to
            // about three quarters of the session length late (measured: +3m44s on a 5-minute session).
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedMillis, intent)
        }
    }

    override fun cancel() = alarmManager.cancel(pendingIntent())

    override val exactNeedsPermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
