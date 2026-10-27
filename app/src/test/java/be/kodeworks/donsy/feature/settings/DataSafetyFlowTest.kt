// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

/** Archive, Recently deleted and the export dialog, through the real screens and database. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class DataSafetyFlowTest {

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

    private fun goBack() {
        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()
    }

    /** Opens Settings and then one of its items, scrolling to it first on the small test screen. */
    private fun openSetting(titleRes: Int) {
        composeRule.onNodeWithContentDescription(string(R.string.action_open_settings)).performClick()
        composeRule.waitUntilExactlyOneExists(hasScrollToNodeAction())
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(titleRes)))
        composeRule.onNodeWithText(string(titleRes)).performClick()
    }

    private fun createDoneTask(title: String) {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        composeRule.onNode(hasText(string(R.string.task_field_title)) and hasSetTextAction()).performTextInput(title)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_mark_task_done)))
        // Below the step field and template suggestion on the small test screen.
        composeRule.onNodeWithText(string(R.string.action_mark_task_done)).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_reopen)))
        goBack()
    }

    @Test
    fun aDoneTaskCanBeArchived_andBroughtBackFromSettings() {
        createDoneTask("Essay")
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.tasks_section_done, 1)))
        composeRule.onNodeWithText(string(R.string.tasks_section_done, 1)).performClick()

        composeRule.waitUntilExactlyOneExists(hasContentDescription(string(R.string.action_archive_named, "Essay")))
        composeRule.onNodeWithContentDescription(string(R.string.action_archive_named, "Essay")).performClick()

        composeRule.waitUntilDoesNotExist(hasText("Essay"))
        composeRule.onNodeWithText(string(R.string.task_archived)).assertExists()

        openSetting(R.string.settings_archive)
        composeRule.waitUntilExactlyOneExists(hasText("Essay"))
        composeRule.onNodeWithContentDescription(string(R.string.action_bring_back_named, "Essay")).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.archive_empty)))

        goBack()
        goBack()
        composeRule.waitUntilExactlyOneExists(hasText("Essay"))
    }

    @Test
    fun aDeletedThoughtCanBeRestoredFromRecentlyDeleted() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_capture)).performClick()
        composeRule.waitUntilExactlyOneExists(hasSetTextAction())
        composeRule.onNode(hasSetTextAction()).performTextInput("Call Sam")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Call Sam"))
        composeRule.onNodeWithText("Call Sam").performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_delete)))
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_empty_title)))

        openSetting(R.string.settings_recently_deleted)
        composeRule.waitUntilExactlyOneExists(hasText("Call Sam"))
        composeRule.onNodeWithContentDescription(string(R.string.action_restore_named, "Call Sam")).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.recently_deleted_empty)))

        goBack()
        goBack()
        composeRule.waitUntilExactlyOneExists(hasText("Call Sam"))
    }

    @Test
    fun exportWarnsAboutPlainFiles_andNeedsMatchingPassphrasesWhenProtected() {
        openSetting(R.string.settings_export)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.export_plain_warning)))

        composeRule.onNodeWithText(string(R.string.export_protect)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.export_passphrase_rule)))
        composeRule.onNode(hasText(string(R.string.action_export)) and hasClickAction()).assertIsNotEnabled()

        composeRule.onNode(hasText(string(R.string.export_passphrase)) and hasSetTextAction()).performTextInput("correct horse")
        composeRule.onNode(hasText(string(R.string.export_passphrase_repeat)) and hasSetTextAction()).performTextInput("correct hose")
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.export_passphrase_mismatch)))
        composeRule.onNode(hasText(string(R.string.action_export)) and hasClickAction()).assertIsNotEnabled()

        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        composeRule.waitUntilDoesNotExist(hasText(string(R.string.export_plain_warning)))
    }
}
