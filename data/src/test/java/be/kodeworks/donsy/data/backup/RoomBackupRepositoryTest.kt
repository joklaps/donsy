// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.backup

import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.db.entity.FocusSessionEntity
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.data.preferences.testPreferences
import be.kodeworks.donsy.data.repository.RoomBrainDumpRepository
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.backup.Backup
import be.kodeworks.donsy.domain.backup.BackupReadResult
import be.kodeworks.donsy.domain.backup.BackupSummary
import be.kodeworks.donsy.domain.backup.RestoreResult
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.time.FakeTimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class RoomBackupRepositoryTest {

    private val time = FakeTimeSource()

    /** One phone's worth of Donsy: its database, settings and backup repository. */
    private inner class Device {
        val database: DonsyDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val preferences: UserPreferencesRepository = testPreferences()
        val tasks = RoomTaskRepository(database, time)
        val brainDump = RoomBrainDumpRepository(database.brainDumpDao(), time)
        val backups = RoomBackupRepository(database, preferences, time)
    }

    private val phone = Device()
    private val newPhone = Device()

    @After
    fun tearDown() {
        phone.database.close()
        newPhone.database.close()
    }

    /** A bit of everything: steps, a routine, done, archived and deleted tasks, notes and sessions. */
    private suspend fun Device.fill() {
        val essay = tasks.create(
            TaskDraft(
                title = "Essay",
                notes = "History, 2 pages",
                importance = Importance.HIGH,
                dueDate = LocalDate.of(2026, 11, 3),
                dueTime = LocalTime.of(8, 30),
                estimateMinutes = 30,
                effort = Effort.MEDIUM,
            ),
        )!!
        tasks.addSteps(essay, listOf("Outline", "Write", "Check"))
        val outline = tasks.observeTask(essay).first()!!.subtasks.first().id
        tasks.setStepDone(outline, true)
        val pill = tasks.create(TaskDraft(title = "Pill", repeatDays = RepeatDays.EVERY_DAY, dueTime = LocalTime.of(8, 0)))!!
        tasks.setRoutineDone(pill, true)
        val done = tasks.create(TaskDraft(title = "Done"))!!
        tasks.complete(done, alsoCompleteOpenSteps = false)
        val archived = tasks.create(TaskDraft(title = "Archived"))!!
        tasks.complete(archived, alsoCompleteOpenSteps = false)
        tasks.archive(archived)
        val gone = tasks.create(TaskDraft(title = "Gone"))!!
        tasks.delete(gone)

        brainDump.capture("Buy a birthday card")
        val convert = brainDump.capture("Plan the party")!!
        tasks.createFromBrainDump(convert, TaskDraft(title = "Plan the party"))
        val oops = brainDump.capture("Oops")!!
        brainDump.delete(oops)

        database.focusSessionDao().insert(
            FocusSessionEntity(
                taskId = essay,
                subtaskId = outline,
                plannedDurationMillis = Duration.ofMinutes(15).toMillis(),
                status = FocusSessionStatus.COMPLETED,
                startedAt = time.now,
                endedAt = time.now.plus(Duration.ofMinutes(15)),
                accumulatedRunMillis = Duration.ofMinutes(15).toMillis(),
                bootCount = 1,
            ),
        )

        preferences.setFocusDurationOffered(45, offered = true)
        preferences.setFocusDefaultMinutes(45)
        preferences.setPreciseEndAlerts(true)
        preferences.setArchiveAfterDays(7)
    }

    private suspend fun Device.read(file: ByteArray, passphrase: String? = null): Backup =
        (backups.read(file, passphrase?.toCharArray()) as BackupReadResult.Ready).backup

    @Test
    fun aRestoredBackupHoldsExactlyWhatWasExported() = runTest {
        phone.fill()
        val file = phone.backups.export(passphrase = null)

        assertEquals(RestoreResult.Restored, newPhone.backups.restore(newPhone.read(file)))

        val before = phone.database.backupDao()
        val after = newPhone.database.backupDao()
        assertEquals(before.tasks(), after.tasks())
        assertEquals(before.subtasks(), after.subtasks())
        assertEquals(before.brainDumpItems(), after.brainDumpItems())
        // Device-bound timer fields are left out of the file.
        assertEquals(
            before.finishedFocusSessions().map { it.copy(bootCount = 0) },
            after.finishedFocusSessions(),
        )
        assertEquals(phone.preferences.backupSettings(), newPhone.preferences.backupSettings())
        assertEquals(listOf(45), newPhone.preferences.focusDurations.first().filter { it == 45 })
    }

    @Test
    fun theSummaryCountsWhatTheBackupHolds() = runTest {
        phone.fill()

        val summary = newPhone.read(phone.backups.export(passphrase = null)).summary

        // Tasks: Essay, Done, Archived, Plan the party. Notes: the one still in the inbox.
        assertEquals(
            BackupSummary(exportedAt = time.now, tasks = 4, routines = 1, notes = 1, focusSessions = 1, recentlyDeleted = 2),
            summary,
        )
    }

    @Test
    fun restoringReplacesEverything() = runTest {
        phone.fill()
        val file = phone.backups.export(passphrase = null)
        newPhone.tasks.create(TaskDraft(title = "Only on the new phone"))
        newPhone.brainDump.capture("Also only here")

        newPhone.backups.restore(newPhone.read(file))

        val titles = newPhone.tasks.observeTasks().first().map { it.title }
        assertFalse("Only on the new phone" in titles)
        assertTrue("Essay" in titles)
        assertEquals(listOf("Buy a birthday card"), newPhone.brainDump.observeInbox().first().map { it.text })
        assertEquals(listOf("Archived"), newPhone.tasks.observeArchived().first().map { it.title })
        assertEquals(listOf("Gone"), newPhone.tasks.observeDeleted().first().map { it.title })
    }

    @Test
    fun aPassphraseProtectedFileNeedsTheRightPassphrase() = runTest {
        phone.fill()
        val file = phone.backups.export(passphrase = "correct horse".toCharArray())

        assertFalse("the contents aren't readable", "Essay" in file.decodeToString())
        assertEquals(BackupReadResult.NeedsPassphrase, newPhone.backups.read(file, passphrase = null))
        assertEquals(BackupReadResult.WrongPassphrase, newPhone.backups.read(file, "wrong".toCharArray()))

        newPhone.backups.restore(newPhone.read(file, "correct horse"))
        assertEquals(phone.database.backupDao().tasks(), newPhone.database.backupDao().tasks())
    }

    @Test
    fun aRunningFocusSessionIsNotExported_andBlocksARestore() = runTest {
        phone.fill()
        val running = FocusSessionEntity(
            plannedDurationMillis = Duration.ofMinutes(15).toMillis(),
            status = FocusSessionStatus.RUNNING,
            startedAt = time.now,
            bootCount = 1,
        )
        phone.database.focusSessionDao().insert(running)
        val file = phone.backups.export(passphrase = null)
        assertEquals(1, newPhone.read(file).summary.focusSessions)

        newPhone.database.focusSessionDao().insert(running)
        newPhone.tasks.create(TaskDraft(title = "Still here"))

        assertEquals(RestoreResult.FocusSessionActive, newPhone.backups.restore(newPhone.read(file)))
        assertEquals(listOf("Still here"), newPhone.tasks.observeTasks().first().map { it.title })
    }

    @Test
    fun otherFilesAreRecognisedAsNotABackup() = runTest {
        val notBackups = listOf(
            "Hello",
            "{}",
            """{"format":"something-else","formatVersion":1}""",
            """{"format":"donsy-backup","formatVersion":1}""",
        )
        notBackups.forEach { text ->
            assertEquals(text, BackupReadResult.NotABackup, newPhone.backups.read(text.encodeToByteArray(), passphrase = null))
        }
    }

    @Test
    fun aFileWithBrokenLinksOrUnknownValuesIsNotABackup() = runTest {
        phone.fill()
        val file = phone.backups.export(passphrase = null).decodeToString()

        val missingTask = file.replace("\"steps\":[{\"id\":1,\"taskId\":1", "\"steps\":[{\"id\":1,\"taskId\":999")
        val unknownStatus = file.replace("\"status\":\"ARCHIVED\"", "\"status\":\"SOMEDAY\"")

        assertTrue(missingTask != file && unknownStatus != file)
        assertEquals(BackupReadResult.NotABackup, newPhone.backups.read(missingTask.encodeToByteArray(), passphrase = null))
        assertEquals(BackupReadResult.NotABackup, newPhone.backups.read(unknownStatus.encodeToByteArray(), passphrase = null))
    }

    @Test
    fun aFileFromANewerVersionIsRecognised() = runTest {
        val newer = phone.backups.export(passphrase = null).decodeToString().replace("\"formatVersion\":1", "\"formatVersion\":2")

        assertEquals(BackupReadResult.FromNewerVersion, newPhone.backups.read(newer.encodeToByteArray(), passphrase = null))
    }

    @Test
    fun theFileIsVersionedPlainJson() = runTest {
        phone.fill()

        val text = phone.backups.export(passphrase = null).decodeToString()

        assertTrue(text.startsWith("{\"format\":\"donsy-backup\",\"formatVersion\":1,"))
        assertTrue("\"title\":\"Essay\"" in text)
        assertTrue("\"dueDate\":\"2026-11-03\"" in text)
        assertTrue(TaskStatus.ARCHIVED.name in text)
    }
}
