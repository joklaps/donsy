// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.ui.components.DonsyCard

/**
 * Asks for what routines need, one permission at a time. Notifications are requested in-app the
 * first time; after that (and for alarms, always) Android's own settings screen opens. Callers
 * check again when their screen resumes, and [onResult] covers the in-app request.
 */
@Composable
fun ReminderPermissionCard(
    missing: MissingReminderPermission,
    message: String,
    notificationsAskedBefore: Boolean,
    onNotificationsAsked: () -> Unit,
    onResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    DonsyCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                when (missing) {
                    MissingReminderPermission.NOTIFICATIONS -> {
                        val canAskInApp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAskedBefore &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (canAskInApp) {
                            onNotificationsAsked()
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    }
                    // Only ever missing on Android 12+.
                    MissingReminderPermission.ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }
            }) {
                Text(
                    stringResource(
                        when (missing) {
                            MissingReminderPermission.NOTIFICATIONS -> R.string.action_allow_notifications
                            MissingReminderPermission.ALARMS -> R.string.action_allow_alarms
                        },
                    ),
                )
            }
        }
    }
}
