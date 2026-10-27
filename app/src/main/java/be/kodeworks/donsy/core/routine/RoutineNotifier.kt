// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.routine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import be.kodeworks.donsy.MainActivity
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.locale.withAppLanguage
import be.kodeworks.donsy.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

/** One notification per routine, with a "Done" action. */
interface RoutineNotifier {
    fun show(task: Task)

    /** Removes routine notifications for every task not in [taskIds]. */
    fun retainOnly(taskIds: Set<Long>)
}

class AndroidRoutineNotifier @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : RoutineNotifier {

    private val manager = NotificationManagerCompat.from(appContext)

    override fun show(task: Task) {
        if (!manager.areNotificationsEnabled()) return
        val context = appContext.withAppLanguage()
        ensureChannel(context)
        val time = task.dueTime?.let { formatTime(context, it) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_routine)
            .setContentTitle(task.title)
            .apply { if (time != null) setContentText(context.getString(R.string.notification_routine_text, time)) }
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openTasks(task.id))
            .addAction(0, context.getString(R.string.action_routine_done), markDone(task.id))
            .build()
        try {
            manager.notify(TAG, task.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Notifications were switched off between the check and here.
        }
    }

    override fun retainOnly(taskIds: Set<Long>) {
        appContext.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.tag == TAG && it.id.toLong() !in taskIds }
            .forEach { manager.cancel(TAG, it.id) }
    }

    /** Channel names follow the app language, so the channel is (re)created with current strings. */
    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notification_channel_routines), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notification_channel_routines_description) },
        )
    }

    /** Locale-aware and following the phone's 12/24-hour setting. */
    private fun formatTime(context: Context, time: LocalTime): String {
        val today = time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()
        return DateFormat.getTimeFormat(context).format(Date.from(today))
    }

    private fun openTasks(taskId: Long): PendingIntent = PendingIntent.getActivity(
        appContext,
        taskId.toInt(),
        Intent(appContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun markDone(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        taskId.toInt(),
        Intent(appContext, RoutineAlarmReceiver::class.java)
            .setAction(RoutineAlarmReceiver.ACTION_DONE)
            .putExtra(RoutineAlarmReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL = "routines"
        const val TAG = "routine"
    }
}
