// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
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

/** End-to-end BrainDump flows through the real ViewModels and an in-memory database. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class BrainDumpFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
    }

    // The search field above the list is a text field too; the capture and edit sheets use Done.
    private val thoughtField = hasSetTextAction() and !hasImeAction(ImeAction.Search)
    private val searchField = hasSetTextAction() and hasImeAction(ImeAction.Search)

    // Icon only: the current order is the button's state for screen readers.
    private fun sortButtonIn(@StringRes order: Int) =
        hasContentDescription(string(R.string.braindump_sort_description)) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, string(order))

    private fun clickCaptureButton() {
        composeRule.onNodeWithContentDescription(string(R.string.action_capture)).performClick()
    }

    private fun capture(text: String) {
        clickCaptureButton()
        composeRule.waitUntilExactlyOneExists(thoughtField)
        composeRule.onNode(thoughtField).performTextInput(text)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilDoesNotExist(thoughtField)
    }

    @Test
    fun captureFromBrainDumpAddsTheThoughtToTheList() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_empty_title)))

        capture("Buy a birthday card")

        composeRule.waitUntilExactlyOneExists(hasText("Buy a birthday card"))
        composeRule.onNodeWithText(string(R.string.braindump_empty_title)).assertDoesNotExist()
        // What the BrainDump is for is explained on request, behind the ? in the heading.
        composeRule.onNodeWithText(string(R.string.braindump_intro)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.action_show_help), useUnmergedTree = true).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_intro)))
        composeRule.onNodeWithContentDescription(string(R.string.action_show_help), useUnmergedTree = true).performClick()
        composeRule.waitUntilDoesNotExist(hasText(string(R.string.braindump_intro)))
    }

    @Test
    fun voiceHintIsShownUntilTheFirstCapture() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        clickCaptureButton()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_voice_hint)))
        composeRule.onNode(thoughtField).performTextInput("First thought")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("First thought"))

        clickCaptureButton()
        composeRule.waitUntilExactlyOneExists(thoughtField)
        composeRule.onNodeWithText(string(R.string.braindump_voice_hint)).assertDoesNotExist()
    }

    @Test
    fun editingChangesTheThought() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Draft")
        composeRule.waitUntilExactlyOneExists(hasText("Draft"))

        composeRule.onNodeWithText("Draft").performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_edit_title)))
        composeRule.onNode(thoughtField).performTextInput(" for the talk")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Draft for the talk"))
    }

    @Test
    fun deletingCanBeUndone() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Maybe later")
        composeRule.waitUntilExactlyOneExists(hasText("Maybe later"))

        composeRule.onNodeWithText("Maybe later").performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_delete)))
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_deleted)))
        composeRule.onNodeWithText("Maybe later").assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Maybe later"))
    }

    @Test
    fun captureIsOfferedInBrainDumpButNotOnHome() {
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_welcome_title)))
        composeRule.onNodeWithContentDescription(string(R.string.action_capture)).assertDoesNotExist()

        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        composeRule.waitUntilExactlyOneExists(hasContentDescription(string(R.string.action_capture)))
    }

    @Test
    fun searchShowsOnlyMatchingThoughts() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Buy a birthday card")
        capture("Call the dentist")
        composeRule.waitUntilExactlyOneExists(hasText("Call the dentist"))

        composeRule.onNode(searchField).performTextInput("BIRTHDAY")

        composeRule.waitUntilDoesNotExist(hasText("Call the dentist"))
        composeRule.onNodeWithText("Buy a birthday card").assertExists()
        composeRule.onNodeWithText(string(R.string.braindump_intro)).assertDoesNotExist()

        composeRule.onNodeWithContentDescription(string(R.string.braindump_search_clear)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Call the dentist"))
        composeRule.onNodeWithText("Buy a birthday card").assertExists()
    }

    @Test
    fun searchWithoutMatchSaysSoCalmly() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Buy a birthday card")
        composeRule.waitUntilExactlyOneExists(hasText("Buy a birthday card"))

        composeRule.onNode(searchField).performTextInput("dentist")

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_search_no_match)))
        composeRule.onNodeWithText("Buy a birthday card").assertDoesNotExist()
    }

    @Test
    fun capturingClearsTheSearch() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Buy a birthday card")
        composeRule.waitUntilExactlyOneExists(hasText("Buy a birthday card"))
        composeRule.onNode(searchField).performTextInput("birthday")

        capture("Call the dentist")

        composeRule.waitUntilExactlyOneExists(hasText("Call the dentist"))
        composeRule.onNodeWithText("Buy a birthday card").assertExists()
    }

    @Test
    fun sortButtonShowsTheCurrentOrder() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        capture("Buy a birthday card")
        composeRule.waitUntilExactlyOneExists(sortButtonIn(R.string.braindump_sort_newest))

        composeRule.onNodeWithContentDescription(string(R.string.braindump_sort_description)).performClick()

        composeRule.waitUntilExactlyOneExists(sortButtonIn(R.string.braindump_sort_oldest))
    }
}
