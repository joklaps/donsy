// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import be.kodeworks.donsy.ui.navigation.DonsyApp
import be.kodeworks.donsy.ui.theme.DonsyTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) so per-app languages work on Android 10–12.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** Bumped each time a notification asks to show the Focus tab. */
    private var openFocusRequests by mutableIntStateOf(0)

    /** Bumped each time a routine reminder is tapped: show the Tasks tab. */
    private var openTasksRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            DonsyTheme {
                DonsyApp(openFocusRequests = openFocusRequests, openTasksRequests = openTasksRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_FOCUS, false) == true) openFocusRequests++
        if (intent?.getBooleanExtra(EXTRA_OPEN_TASKS, false) == true) openTasksRequests++
    }

    companion object {
        const val EXTRA_OPEN_FOCUS = "be.kodeworks.donsy.OPEN_FOCUS"
        const val EXTRA_OPEN_TASKS = "be.kodeworks.donsy.OPEN_TASKS"
    }
}
