// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import be.kodeworks.donsy.HiltTestActivity
import be.kodeworks.donsy.R
import be.kodeworks.donsy.testing.assertTappablesAreAccessible
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

/**
 * Every main screen, with content in it, checked for tappable elements without a name or with a
 * touch target below 48 dp (see [assertTappablesAreAccessible]).
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class AccessibilityChecksTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
    }

    private fun string(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun check() {
        composeRule.waitForIdle()
        composeRule.assertTappablesAreAccessible()
    }

    private fun capture(text: String) {
        composeRule.onNodeWithContentDescription(string(R.string.action_capture)).performClick()
        composeRule.waitUntilExactlyOneExists(hasSetTextAction() and hasText(string(R.string.braindump_capture_placeholder)))
        composeRule.onNode(hasSetTextAction() and hasText(string(R.string.braindump_capture_placeholder))).performTextInput(text)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(text))
    }

    private fun createTask(title: String, done: Boolean = false) {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        composeRule.onNode(hasText(string(R.string.task_field_title)) and hasSetTextAction()).performTextInput(title)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_mark_task_done)))
        check()
        if (done) {
            composeRule.onNodeWithText(string(R.string.action_mark_task_done)).performScrollTo().performClick()
            composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_reopen)))
        }
        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()
    }

    @Test
    fun theMainScreensPassAccessibilityChecks() {
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_welcome_title)))
        check()

        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Call Sam about the party")
        check()

        createTask("Essay")
        createTask("Old homework", done = true)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.tasks_section_done, 1)))
        composeRule.onNodeWithText(string(R.string.tasks_section_done, 1)).performClick()
        check()

        composeRule.onNode(navItem(R.string.tab_home)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_suggestion_label)))
        check()

        composeRule.onNode(navItem(R.string.tab_focus)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_setup_title)))
        check()
    }

    @Test
    fun theTaskEditorPassesAccessibilityChecks() {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        check()
    }

    @Test
    fun settingsAndItsScreensPassAccessibilityChecks() {
        composeRule.onNodeWithContentDescription(string(R.string.action_open_settings)).performClick()
        composeRule.waitUntilExactlyOneExists(hasScrollToNodeAction())
        check()

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(R.string.settings_export)))
        composeRule.onNodeWithText(string(R.string.settings_export)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.export_plain_warning)))
        composeRule.onNodeWithText(string(R.string.export_protect)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.export_passphrase_rule)))
        check()
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(R.string.settings_recently_deleted)))
        composeRule.onNodeWithText(string(R.string.settings_recently_deleted)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.recently_deleted_empty)))
        check()
        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(R.string.settings_archive)))
        composeRule.onNodeWithText(string(R.string.settings_archive)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.archive_empty)))
        check()
        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(R.string.settings_licenses)))
        composeRule.onNodeWithText(string(R.string.settings_licenses)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.licenses_donsy_body)))
        check()
    }
}
