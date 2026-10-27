// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class UserPreferencesRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun CoroutineScope.repository() = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = this) { File(tempFolder.root, "test.preferences_pb") },
    )

    @Test
    fun voiceHintIsUnseenByDefault() = runTest {
        assertFalse(backgroundScope.repository().voiceHintSeen.first())
    }

    @Test
    fun markVoiceHintSeenPersists() = runTest {
        val repository = backgroundScope.repository()
        repository.markVoiceHintSeen()
        assertTrue(repository.voiceHintSeen.first())
    }

    @Test
    fun focusLengthsDefaultTo5To25With15Preselected() = runTest {
        val repository = backgroundScope.repository()
        assertEquals(listOf(5, 10, 15, 25), repository.focusDurations.first())
        assertEquals(15, repository.focusDefaultMinutes.first())
    }

    @Test
    fun offeredLengthsAndTheDefaultCanBeChanged() = runTest {
        val repository = backgroundScope.repository()
        repository.setFocusDurationOffered(45, offered = true)
        repository.setFocusDurationOffered(5, offered = false)
        repository.setFocusDefaultMinutes(45)

        assertEquals(listOf(10, 15, 25, 45), repository.focusDurations.first())
        assertEquals(45, repository.focusDefaultMinutes.first())
    }

    @Test
    fun removingTheDefaultLengthPreselectsTheClosestTo15() = runTest {
        val repository = backgroundScope.repository()
        repository.setFocusDefaultMinutes(25)
        repository.setFocusDurationOffered(25, offered = false)
        repository.setFocusDurationOffered(15, offered = false)

        assertEquals(10, repository.focusDefaultMinutes.first())
    }

    @Test
    fun theLastOfferedLengthCannotBeRemoved() = runTest {
        val repository = backgroundScope.repository()
        listOf(5, 10, 15, 25).forEach { repository.setFocusDurationOffered(it, offered = false) }

        assertEquals(listOf(25), repository.focusDurations.first())
    }
}
