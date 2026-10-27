// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.feature.braindump.capture.CaptureViewModel
import be.kodeworks.donsy.testing.FakeBrainDumpRepository
import be.kodeworks.donsy.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repository = FakeBrainDumpRepository(FakeTimeSource())

    private fun CoroutineScope.viewModel(): Pair<CaptureViewModel, UserPreferencesRepository> {
        val preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = this) { File(tempFolder.root, "prefs.preferences_pb") },
        )
        return CaptureViewModel(repository, preferences) to preferences
    }

    @Test
    fun voiceHintShowsUntilTheFirstThoughtIsSaved() = runTest {
        val (viewModel, _) = backgroundScope.viewModel()

        viewModel.showVoiceHint.test {
            assertFalse(awaitItem()) // initial value before preferences load
            assertTrue(awaitItem())
            viewModel.capture("first thought")
            assertFalse(awaitItem())
        }
    }

    @Test
    fun captureStoresTheThoughtAndReportsIt() = runTest {
        val (viewModel, _) = backgroundScope.viewModel()

        viewModel.saved.test {
            viewModel.capture("call grandma")
            awaitItem()
        }
        assertEquals(listOf("call grandma"), repository.observeInbox().first().map { it.text })
    }

    @Test
    fun blankCaptureIsIgnoredAndKeepsTheHint() = runTest {
        val (viewModel, preferences) = backgroundScope.viewModel()

        viewModel.saved.test {
            viewModel.capture("   ")
            expectNoEvents()
        }
        assertFalse(preferences.voiceHintSeen.first())
        assertTrue(repository.observeInbox().first().isEmpty())
    }
}
