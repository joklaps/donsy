// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import be.kodeworks.donsy.HiltTestActivity
import be.kodeworks.donsy.R
import be.kodeworks.donsy.testing.navItem
import be.kodeworks.donsy.testing.string
import be.kodeworks.donsy.ui.theme.DonsyTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// Android 10 (minSdk), 12 (dynamic color) and 16 (targetSdk).
@Config(sdk = [29, 31, 36])
class DonsyAppNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
    }

    @Test
    fun startsOnHome() {
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.home_welcome_title)))
        composeRule.onNode(navItem(R.string.tab_home)).assertIsSelected()
    }

    @Test
    fun bottomNavigationSwitchesTabs() {
        listOf(
            R.string.tab_braindump to R.string.braindump_empty_title,
            R.string.tab_tasks to R.string.tasks_empty_title,
            R.string.tab_focus to R.string.focus_setup_title,
            R.string.tab_home to R.string.home_welcome_title,
        ).forEach { (tab, content) ->
            composeRule.onNode(navItem(tab)).performClick()
            // BrainDump's empty state appears once the database has answered.
            composeRule.waitUntilExactlyOneExists(hasText(string(content)))
        }
    }

    @Test
    fun settingsOpensFromTabAndGoesBack() {
        composeRule.onNode(navItem(R.string.tab_tasks)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.action_open_settings)).performClick()

        composeRule.onNodeWithText(string(R.string.settings_language)).assertExists()
        composeRule.onNode(navItem(R.string.tab_home)).assertDoesNotExist()

        composeRule.onNodeWithContentDescription(string(R.string.action_navigate_back)).performClick()
        composeRule.onNodeWithText(string(R.string.tasks_empty_title)).assertExists()
    }
}
