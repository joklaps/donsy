// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.focus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import be.kodeworks.donsy.MainActivity
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.locale.withAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/** Notifications for focus sessions. All of them are skipped when notifications aren't allowed. */
interface FocusNotifier {
    /** Ongoing, silent countdown that ends at [endsAt]. */
    fun showRunning(targetTitle: String?, endsAt: Instant)

    fun showPaused(targetTitle: String?, minutesLeft: Int)

    fun cancelOngoing()

    /** A session ran out of time: one alerting notification. */
    fun showCompleted()
}

class AndroidFocusNotifier @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : FocusNotifier {

    private val manager = NotificationManagerCompat.from(appContext)

    override fun showRunning(targetTitle: String?, endsAt: Instant) {
        val context = localized()
        notify(
            ONGOING_ID,
            NotificationCompat.Builder(context, CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(ongoingTitle(context, targetTitle))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endsAt.toEpochMilli())
                // The system removes it right when the time is up, so the countdown never goes
                // negative while the (possibly inexact) end alarm is still on its way.
                .setTimeoutAfter((endsAt.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1))
                .setShowWhen(true)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openFocus())
                .build(),
        )
    }

    override fun showPaused(targetTitle: String?, minutesLeft: Int) {
        val context = localized()
        notify(
            ONGOING_ID,
            NotificationCompat.Builder(context, CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(ongoingTitle(context, targetTitle))
                .setContentText(context.resources.getQuantityString(R.plurals.notification_focus_paused, minutesLeft, minutesLeft))
                .setShowWhen(false)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openFocus())
                .build(),
        )
    }

    override fun cancelOngoing() = manager.cancel(ONGOING_ID)

    override fun showCompleted() {
        val context = localized()
        notify(
            COMPLETED_ID,
            NotificationCompat.Builder(context, CHANNEL_ENDED)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(context.getString(R.string.notification_focus_done_title))
                .setContentText(context.getString(R.string.notification_focus_done_text))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openFocus())
                .build(),
        )
    }

    private fun ongoingTitle(context: Context, targetTitle: String?) =
        if (targetTitle == null) {
            context.getString(R.string.notification_focus_running_plain)
        } else {
            context.getString(R.string.notification_focus_running_title, targetTitle)
        }

    private fun notify(id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        ensureChannels(localized())
        manager.notify(id, notification)
    }

    /** Channel names follow the app language, so they are (re)created with current strings. */
    private fun ensureChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.notification_channel_focus), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.notification_channel_focus_description) },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ENDED, context.getString(R.string.notification_channel_focus_end), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notification_channel_focus_end_description) },
        )
    }

    private fun localized(): Context = appContext.withAppLanguage()

    private fun openFocus(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_RUNNING = "focus_session"
        const val CHANNEL_ENDED = "focus_session_ended"
        const val ONGOING_ID = 1
        const val COMPLETED_ID = 2
    }
}
