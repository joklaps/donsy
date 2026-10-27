// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.routine

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What Android still has to allow before routines can remind on time, asked for one at a time. */
enum class MissingReminderPermission { NOTIFICATIONS, ALARMS }

/**
 * Routines can't be used without both: a reminder that may arrive late or not at all is no help
 * for "take my pill at 8:00". Both can be withdrawn in system settings at any moment, so callers
 * check again when their screen resumes.
 */
interface ReminderPermissions {
    /** Null when everything is allowed; otherwise the next thing to ask for. */
    fun missing(): MissingReminderPermission?

    /** Exact alarms; before Android 12 they need no permission. */
    fun alarmsAllowed(): Boolean
}

class AndroidReminderPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderPermissions {

    private val alarmManager: AlarmManager = checkNotNull(context.getSystemService())

    override fun missing(): MissingReminderPermission? = when {
        // Covers both the Android 13+ runtime permission and notifications switched off by the user.
        !NotificationManagerCompat.from(context).areNotificationsEnabled() -> MissingReminderPermission.NOTIFICATIONS
        !alarmsAllowed() -> MissingReminderPermission.ALARMS
        else -> null
    }

    override fun alarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
