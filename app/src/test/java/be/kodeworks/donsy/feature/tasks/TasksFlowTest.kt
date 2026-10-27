// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import be.kodeworks.donsy.HiltTestActivity
import be.kodeworks.donsy.R
import be.kodeworks.donsy.testing.navItem
import be.kodeworks.donsy.testing.quantityString
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

/** End-to-end task flows through the real ViewModels and an in-memory database. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class TasksFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
    }

    /** Creates a task from the Tasks tab; ends on its detail screen. */
    private fun createTask(title: String) {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))
        composeRule.onNode(hasText(string(R.string.task_field_title)) and hasSetTextAction()).performTextInput(title)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_mark_task_done)))
    }

    private fun addStep(title: String) {
        val field = hasText(string(R.string.task_add_step_placeholder)) and hasSetTextAction()
        composeRule.onNode(field).performTextInput(title)
        composeRule.onNode(hasSetTextAction() and hasText(title)).performImeAction()
        composeRule.waitUntilExactlyOneExists(hasText(title) and !hasSetTextAction())
    }

    private fun stepRow(title: String) =
        hasAnyDescendant(hasText(title)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

    @Test
    fun aNewTaskOpensReadyToBeBrokenIntoSteps() {
        createTask("Prepare for my biology test")

        composeRule.onNodeWithText("Prepare for my biology test").assertExists()
        composeRule.onNodeWithText(string(R.string.task_steps_empty)).assertExists()
        addStep("Find the test topics")
        addStep("Make flashcards")
        composeRule.onNodeWithText(string(R.string.task_steps_empty)).assertDoesNotExist()
    }

    @Test
    fun editorHeadingsExplainThemselvesOnRequest() {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_new_task)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)))

        val help = hasText(string(R.string.task_field_estimate_help))
        composeRule.onNode(help).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.task_field_estimate)).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(help)
        composeRule.onNodeWithText(string(R.string.task_field_estimate)).performClick()
        composeRule.waitUntilDoesNotExist(help)
        // The switch's explanation is always shown.
        composeRule.onNodeWithText(string(R.string.task_field_important_help)).assertExists()
    }

    @Test
    fun checkingOffTheLastStepOffersToFinishTheTask() {
        createTask("Clean my room")
        addStep("Desk")

        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.last_step_title)))
        composeRule.onNodeWithText(string(R.string.action_mark_done)).performClick()

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.action_reopen)))
        composeRule.onNodeWithText(string(R.string.task_status_done)).assertExists()
    }

    @Test
    fun stepsCanBeReorderedWithAccessibilityActions() {
        createTask("Party")
        addStep("Invite friends")
        addStep("Buy cake")

        composeRule.onNode(stepRow("Buy cake")).performCustomAccessibilityActionWithLabel(string(R.string.action_move_up))

        composeRule.waitUntil {
            val cake = composeRule.onNodeWithText("Buy cake").fetchSemanticsNode().boundsInRoot.top
            val invite = composeRule.onNodeWithText("Invite friends").fetchSemanticsNode().boundsInRoot.top
            cake < invite
        }
        // The first step can only move down.
        composeRule.onNode(stepRow("Buy cake")).performCustomAccessibilityActionWithLabel(string(R.string.action_move_down))
    }

    @Test
    fun deletingFromTheDetailScreenCanBeUndoneInTheList() {
        createTask("Maybe later")

        composeRule.onNodeWithContentDescription(string(R.string.action_more_options)).performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_deleted)))
        composeRule.onNodeWithText("Maybe later").assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Maybe later"))
    }

    @Test
    fun aThoughtCanBeTurnedIntoATask() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_capture)).performClick()
        composeRule.waitUntilExactlyOneExists(hasSetTextAction())
        composeRule.onNode(hasSetTextAction()).performTextInput("Plan Sam's party")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Plan Sam's party"))

        composeRule.onNodeWithText("Plan Sam's party").performClick()
        // The edit sheet's button; the list row has a "Make it a task" chip of its own.
        val sheetButton = hasText(string(R.string.action_make_task)) and !hasContentDescription("Make “Plan Sam's party” a task")
        composeRule.waitUntilExactlyOneExists(sheetButton)
        composeRule.onNode(sheetButton).performClick()

        // The editor opens prefilled with the thought.
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)) and hasText("Plan Sam's party"))
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.braindump_converted)))
        composeRule.waitUntilDoesNotExist(hasText("Plan Sam's party"))

        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Plan Sam's party"))
    }

    @Test
    fun theMakeItATaskChipOpensThePrefilledEditorDirectly() {
        composeRule.onNode(navItem(R.string.tab_braindump)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_capture)).performClick()
        composeRule.waitUntilExactlyOneExists(hasSetTextAction())
        composeRule.onNode(hasSetTextAction()).performTextInput("Buy a birthday card\nFor grandma")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()

        // Named after the thought's first line for screen readers.
        composeRule.waitUntilExactlyOneExists(hasContentDescription("Make “Buy a birthday card” a task"))
        // On the row's bottom line, under the capture button on Robolectric's small screen.
        composeRule.onNodeWithContentDescription("Make “Buy a birthday card” a task").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.task_field_title)) and hasText("Buy a birthday card"))
        composeRule.onNodeWithText("For grandma").assertExists()
    }

    @Test
    fun taskRowCheckboxIsNamedForScreenReaders() {
        createTask("Water the plants")
        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()

        composeRule.waitUntilExactlyOneExists(hasContentDescription("Mark “Water the plants” done"))
    }

    @Test
    fun aMatchingTemplateIsSuggestedAndItsStepsCanBeReviewedFirst() {
        createTask("Prepare for my biology test")

        composeRule.onNodeWithText(string(R.string.action_try_template_steps)).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.template_study_test_name)))
        composeRule.onNodeWithContentDescription("Include “Gather your notes and books”").performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Review the first topic")).performScrollTo().performTextReplacement("Review cells")
        composeRule.onNodeWithText(quantityString(R.plurals.action_add_template_steps_count, 4)).performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Review cells") and !hasSetTextAction())
        composeRule.onNodeWithText("Find out which topics the test covers").assertExists()
        composeRule.onNodeWithText("Gather your notes and books").assertDoesNotExist()
        // With steps in place the suggestion makes way for the plain template button.
        composeRule.onNodeWithText(string(R.string.action_try_template_steps)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.action_add_template_steps)).assertExists()
    }

    @Test
    fun anyTemplateCanBePickedFromTheList() {
        createTask("Weekend in Ghent")

        composeRule.onNodeWithText(string(R.string.action_add_template_steps)).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.template_picker_title)))
        composeRule.onNodeWithText(string(R.string.template_trip_name)).performScrollTo().performClick()
        composeRule.onNodeWithText(quantityString(R.plurals.action_add_template_steps_count, 5)).performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Pack your bag") and !hasSetTextAction())
    }
}
