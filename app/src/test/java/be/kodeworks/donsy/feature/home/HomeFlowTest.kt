// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class HomeFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
    }

    /** Creates a task with one step, then returns to Home. */
    private fun createTaskWithStep(title: String, step: String) {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        composeRule.onNode(hasText(string(R.string.task_field_title)) and hasSetTextAction()).performTextInput(title)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_add_step_placeholder)))
        composeRule.onNode(hasText(string(R.string.task_add_step_placeholder)) and hasSetTextAction()).performTextInput(step)
        composeRule.onNode(hasSetTextAction() and hasText(step)).performImeAction()
        composeRule.waitUntilExactlyOneExists(hasText(step) and !hasSetTextAction())
        composeRule.onNode(navItem(R.string.tab_home)).performClick()
    }

    @Test
    fun firstRunShowsTheWelcome() {
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_welcome_title)))
    }

    @Test
    fun homeSuggestsTheStepAndStartOpensFocusForIt() {
        createTaskWithStep("Biology test", "Find the topics")

        composeRule.waitUntilExactlyOneExists(hasText("Find the topics"))
        composeRule.onNodeWithText("Biology test").assertExists()
        composeRule.onNodeWithText(composeRule.activity.resources.getQuantityString(R.plurals.step_position, 1, 1, 1)).assertExists()
        composeRule.onNodeWithText(string(R.string.reason_good_start)).assertExists()

        // Below the time and energy chips, Start can be off Robolectric's small screen.
        composeRule.onNodeWithText(string(R.string.action_start)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.focus_setup_title)))
        composeRule.onNodeWithText("Find the topics").assertExists()
    }

    @Test
    fun notNowOffersStartersAndCanBeUndone() {
        createTaskWithStep("Laundry", "Sort colours")
        composeRule.waitUntilExactlyOneExists(hasText("Sort colours"))

        // On Robolectric's small default screen the button can be off screen, so click it through its
        // semantics action rather than a touch.
        composeRule.onNodeWithText(string(R.string.action_not_now)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_nothing_title)))
        composeRule.onNodeWithText(string(R.string.starter_add_small_task)).assertExists()

        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Sort colours"))
    }

    @Test
    fun chooseMyselfOpensTheTaskList() {
        createTaskWithStep("Guitar", "Tune it")
        composeRule.waitUntilExactlyOneExists(hasText("Tune it"))

        composeRule.onNodeWithText(string(R.string.action_choose_myself)).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.tasks_section_todo)))
    }
}
