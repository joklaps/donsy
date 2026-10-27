// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import android.Manifest
import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import be.kodeworks.donsy.HiltTestActivity
import be.kodeworks.donsy.R
import be.kodeworks.donsy.testing.navItem
import be.kodeworks.donsy.testing.string
import be.kodeworks.donsy.ui.navigation.DonsyApp
import be.kodeworks.donsy.ui.theme.DonsyTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** Making a routine in the editor and ticking it off in the Tasks list. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class RoutinesFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Robolectric never answers permission screens: grant what routines need up front.
        shadowOf(RuntimeEnvironment.getApplication() as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun start() = composeRule.setContent { DonsyTheme { DonsyApp() } }

    /** Opens the editor with a title and "Every day" chosen. */
    private fun startRoutine(title: String) {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        composeRule.onNode(hasText(string(R.string.task_field_title)) and hasSetTextAction()).performTextInput(title)
        composeRule.onNodeWithText(string(R.string.repeat_every_day)).performClick()
    }

    @Test
    fun aRoutineIsMadeWithATimeAndTickedOffForToday() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        start()
        startRoutine("Take my pill")
        composeRule.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()

        composeRule.onNodeWithText(string(R.string.task_remind_time_none)).performScrollTo().performClick()
        // The time dialog is taller than Robolectric's small screen: confirm through semantics.
        composeRule.onNodeWithText(string(R.string.action_ok)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()

        // Every day includes today, so it can be done for today right away.
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_routine_done_today)))
        composeRule.onNodeWithText(string(R.string.action_routine_done_today)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_routine_not_done)))

        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.tasks_section_routines)))
        val undo = RuntimeEnvironment.getApplication().getString(R.string.action_routine_undo_named, "Take my pill")
        composeRule.waitUntilExactlyOneExists(hasContentDescription(undo))
    }

    @Test
    fun withoutTheAlarmPermissionARoutineCannotBeSaved() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        start()
        startRoutine("Pack my schoolbag")
        composeRule.onNodeWithText(string(R.string.task_remind_time_none)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.action_ok)).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_allow_alarms)))
        composeRule.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()
    }
}
