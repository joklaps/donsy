// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.focus

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
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

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class FocusFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        // Robolectric never answers the permission dialog; on a device the session starts either way.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
    }

    private fun click(textRes: Int) = composeRule.onNodeWithText(string(textRes)).performScrollTo().performClick()

    @Test
    fun aSessionWithoutATaskRunsPausesAndEnds() {
        composeRule.onNode(navItem(R.string.tab_focus)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_target_none)))

        click(R.string.action_start_focus)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_just_focusing)))
        composeRule.onNode(hasContentDescription("15 minutes left")).assertExists()

        click(R.string.action_pause)
        composeRule.waitUntilExactlyOneExists(hasContentDescription("Paused, 15 minutes left"))
        click(R.string.action_resume)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_pause)))

        click(R.string.action_finish_early)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_ended_title)))
        click(R.string.action_start_another)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_start_focus)))
    }

    @Test
    fun startOnHomeOpensFocusWithTheSuggestedStep() {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        composeRule.onNode(hasText(string(R.string.task_field_title)) and hasSetTextAction()).performTextInput("Essay")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_add_step_placeholder)))
        composeRule.onNode(hasText(string(R.string.task_add_step_placeholder)) and hasSetTextAction()).performTextInput("Outline")
        composeRule.onNode(hasSetTextAction() and hasText("Outline")).performImeAction()
        composeRule.waitUntilExactlyOneExists(hasText("Outline") and !hasSetTextAction())

        composeRule.onNode(navItem(R.string.tab_home)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Outline"))
        click(R.string.action_start)

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_setup_title)))
        composeRule.onNodeWithText("Outline").assertExists()
        click(R.string.action_start_focus)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_pause)))
        composeRule.onNodeWithText("Outline").assertExists()

        click(R.string.action_finish_early)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_mark_done_question).format("Outline")))
        click(R.string.action_mark_done)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_marked_done)))
    }
}
