// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings

import android.net.Uri
import app.cash.turbine.test
import be.kodeworks.donsy.data.backup.RoomBackupRepository
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.feature.settings.backup.BackupDialog
import be.kodeworks.donsy.feature.settings.backup.BackupEvent
import be.kodeworks.donsy.feature.settings.backup.BackupMessage
import be.kodeworks.donsy.feature.settings.backup.BackupViewModel
import be.kodeworks.donsy.testing.FakeDocumentFiles
import be.kodeworks.donsy.testing.MainDispatcherRule
import be.kodeworks.donsy.testing.inMemoryDatabase
import be.kodeworks.donsy.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val time = FakeTimeSource() // 2026-10-21 12:00 in Brussels
    private val database = inMemoryDatabase()
    private val tasks = RoomTaskRepository(database, time)
    private val files = FakeDocumentFiles()
    private val exportUri = Uri.parse("content://files/donsy.json")

    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { BackupViewModel(RoomBackupRepository(database, testPreferences(), time), files, time) }

    @After
    fun tearDown() = database.close()

    private suspend fun dialog() = viewModel.uiState.first { !it.isWorking && it.dialog != null }.dialog

    @Test
    fun exportAsksForAFileNamedAfterToday_thenWritesThePlainFileThere() = runTest {
        tasks.create(TaskDraft(title = "Essay"))

        viewModel.events.test {
            viewModel.startExport()
            assertEquals(BackupDialog.ExportOptions, viewModel.uiState.value.dialog)

            viewModel.chooseExportFile(passphrase = null)
            assertEquals(BackupEvent.ChooseExportFile("donsy-2026-10-21.json"), awaitItem())

            viewModel.exportTo(exportUri)
            assertEquals(BackupEvent.Show(BackupMessage.EXPORTED), awaitItem())
        }
        assertTrue("\"title\":\"Essay\"" in files.files.getValue(exportUri).decodeToString())
    }

    @Test
    fun backingOutOfTheFilePickerExportsNothing() = runTest {
        viewModel.chooseExportFile(passphrase = "long enough".toCharArray())
        viewModel.exportTo(null)

        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aFileThatCantBeWrittenIsReported() = runTest {
        files.broken += exportUri
        viewModel.events.test {
            viewModel.chooseExportFile(passphrase = null)
            skipItems(1)
            viewModel.exportTo(exportUri)
            assertEquals(BackupEvent.Show(BackupMessage.EXPORT_FAILED), awaitItem())
        }
    }

    @Test
    fun aProtectedFileAsksForThePassphrase_untilItIsRight_thenConfirmsBeforeRestoring() = runTest {
        tasks.create(TaskDraft(title = "Essay"))
        viewModel.events.test {
            viewModel.chooseExportFile(passphrase = "correct horse".toCharArray())
            skipItems(1) // ChooseExportFile
            viewModel.exportTo(exportUri)
            assertEquals(BackupEvent.Show(BackupMessage.EXPORTED), awaitItem())
            // Changed after the export: the restore brings back the exported state.
            tasks.create(TaskDraft(title = "Made later"))

            viewModel.restoreFrom(exportUri)
            assertEquals(BackupDialog.Passphrase(wasWrong = false), dialog())

            viewModel.submitPassphrase("wrong".toCharArray())
            assertEquals(BackupDialog.Passphrase(wasWrong = true), dialog())

            viewModel.submitPassphrase("correct horse".toCharArray())
            val confirm = dialog() as BackupDialog.ConfirmRestore
            assertEquals(1, confirm.summary.tasks)
            assertEquals(LocalDateTime.of(2026, 10, 21, 12, 0), confirm.exportedAt)

            viewModel.confirmRestore()
            assertEquals(BackupEvent.Show(BackupMessage.RESTORED), awaitItem())
        }
        assertEquals(listOf("Essay"), tasks.observeTasks().first().map { it.title })
    }

    @Test
    fun aFileThatIsNotAnExportIsReported_andNothingChanges() = runTest {
        tasks.create(TaskDraft(title = "Essay"))
        files.files[exportUri] = "shopping list".encodeToByteArray()

        viewModel.events.test {
            viewModel.restoreFrom(exportUri)
            assertEquals(BackupEvent.Show(BackupMessage.NOT_A_BACKUP), awaitItem())
        }
        assertEquals(null, viewModel.uiState.value.dialog)
        assertEquals(listOf("Essay"), tasks.observeTasks().first().map { it.title })
    }

    @Test
    fun aFileThatCantBeReadIsReported() = runTest {
        files.broken += exportUri
        viewModel.events.test {
            viewModel.restoreFrom(exportUri)
            assertEquals(BackupEvent.Show(BackupMessage.UNREADABLE), awaitItem())
        }
    }
}
