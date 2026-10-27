// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.kodeworks.donsy.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fired by [FocusAlarmScheduler] when a session's time is up. */
@AndroidEntryPoint
class FocusAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var focusSessionManager: FocusSessionManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        applicationScope.launch {
            try {
                focusSessionManager.recover(userPresent = false)
            } finally {
                pending.finish()
            }
        }
    }
}
