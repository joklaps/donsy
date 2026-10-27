// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import be.kodeworks.donsy.HiltTestActivity
import be.kodeworks.donsy.R
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

/** Settings → Open source licenses, and the full license texts it opens. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class LicensesFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent { DonsyTheme { DonsyApp() } }
        composeRule.onNodeWithContentDescription(string(R.string.action_open_settings)).performClick()
        composeRule.waitUntilExactlyOneExists(hasScrollToNodeAction())
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(string(R.string.settings_licenses)))
        composeRule.onNodeWithText(string(R.string.settings_licenses)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.licenses_donsy_body)))
    }

    @Test
    fun theGplOpensInFull() {
        composeRule.onNodeWithText(string(R.string.action_read_gpl)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.license_gpl_3_name)))
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("TERMS AND CONDITIONS"))
    }

    @Test
    fun theLibrariesAndTheApacheLicenseAreListed() {
        val list = composeRule.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("Dagger and Hilt"))
        list.performScrollToNode(hasText(string(R.string.action_read_apache)))
        composeRule.onNodeWithText(string(R.string.action_read_apache)).performClick()
        composeRule.waitUntilExactlyOneExists(hasText(string(R.string.license_apache_2_name)))
        composeRule.waitUntilExactlyOneExists(hasText("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
    }
}
