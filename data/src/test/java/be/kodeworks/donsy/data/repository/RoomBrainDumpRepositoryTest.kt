// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.maintenance.DataPurger
import be.kodeworks.donsy.data.preferences.testPreferences
import be.kodeworks.donsy.domain.model.BrainDumpStatus
import be.kodeworks.donsy.domain.time.FakeTimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class RoomBrainDumpRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = FakeTimeSource()
    private val repository = RoomBrainDumpRepository(database.brainDumpDao(), time)
    private val purger = DataPurger(database.brainDumpDao(), database.taskDao(), database.energyCheckInDao(), testPreferences(), time)

    @After
    fun tearDown() = database.close()

    @Test
    fun captureTrimsTextAndStoresAnInboxItem() = runTest {
        val id = repository.capture("  Buy a birthday card for Sam  ")

        val inbox = repository.observeInbox().first()
        assertEquals(listOf(id), inbox.map { it.id })
        with(inbox.single()) {
            assertEquals("Buy a birthday card for Sam", text)
            assertEquals(BrainDumpStatus.INBOX, status)
            assertEquals(time.now, createdAt)
            assertNull(convertedTaskId)
        }
    }

    @Test
    fun blankCaptureIsIgnored() = runTest {
        assertNull(repository.capture("   \n "))
        assertTrue(repository.observeInbox().first().isEmpty())
    }

    @Test
    fun inboxIsNewestFirst() = runTest {
        repository.capture("first")
        time.advanceBy(Duration.ofMinutes(1))
        repository.capture("second")

        assertEquals(listOf("second", "first"), repository.observeInbox().first().map { it.text })
    }

    @Test
    fun updateTextChangesTextAndUpdatedAtButIgnoresBlank() = runTest {
        val id = repository.capture("draft")!!
        time.advanceBy(Duration.ofMinutes(5))

        repository.updateText(id, " final ")
        repository.updateText(id, "  ")

        val item = repository.observeInbox().first().single()
        assertEquals("final", item.text)
        assertEquals(time.now, item.updatedAt)
    }

    @Test
    fun deleteHidesItemAndRestoreBringsItBack() = runTest {
        val id = repository.capture("maybe later")!!

        repository.delete(id)
        assertTrue(repository.observeInbox().first().isEmpty())

        repository.restore(id)
        assertEquals(listOf(id), repository.observeInbox().first().map { it.id })
    }

    @Test
    fun purgeRemovesOnlyItemsDeletedLongerThanRetentionAgo() = runTest {
        val old = repository.capture("old")!!
        val recent = repository.capture("recent")!!
        repository.delete(old)
        time.advanceBy(Duration.ofDays(6))
        repository.delete(recent)
        time.advanceBy(Duration.ofDays(2))

        purger.purge()

        val dao = database.brainDumpDao()
        assertNull(dao.getById(old))
        assertEquals("recent", dao.getById(recent)?.text)
    }

    @Test
    fun recentlyDeletedListsDeletedItems_deleteForeverRemovesOnlyThose() = runTest {
        val kept = repository.capture("kept")!!
        val deleted = repository.capture("deleted")!!
        repository.delete(deleted)

        assertEquals(listOf("deleted"), repository.observeDeleted().first().map { it.text })

        repository.deleteForever(kept)
        repository.deleteForever(deleted)

        assertEquals(listOf(kept), repository.observeInbox().first().map { it.id })
        assertTrue(repository.observeDeleted().first().isEmpty())
        assertNull(database.brainDumpDao().getById(deleted))
    }

    @Test
    fun oldThoughtsStayInTheInbox_onlyDeletedOnesArePurged() = runTest {
        val kept = repository.capture("An idea from last year")!!
        time.advanceBy(Duration.ofDays(400))

        // Automatic archiving is on by default; it is for done tasks only.
        purger.purge()

        assertEquals(listOf(kept), repository.observeInbox().first().map { it.id })
    }
}
