// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.core.backup.DocumentFiles
import be.kodeworks.donsy.domain.backup.Backup
import be.kodeworks.donsy.domain.backup.BackupReadResult
import be.kodeworks.donsy.domain.backup.BackupRepository
import be.kodeworks.donsy.domain.backup.BackupSummary
import be.kodeworks.donsy.domain.backup.RestoreResult
import be.kodeworks.donsy.domain.time.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface BackupDialog {
    /** Before exporting: plain file or passphrase. */
    data object ExportOptions : BackupDialog

    /** The chosen file is protected. */
    data class Passphrase(val wasWrong: Boolean) : BackupDialog

    /** [exportedAt] is the summary's moment in the user's time zone. */
    data class ConfirmRestore(val summary: BackupSummary, val exportedAt: LocalDateTime) : BackupDialog
}

enum class BackupMessage { EXPORTED, EXPORT_FAILED, RESTORED, UNREADABLE, NOT_A_BACKUP, FROM_NEWER_VERSION, FOCUS_SESSION_ACTIVE }

sealed interface BackupEvent {
    /** Open the system's "save as" picker, suggesting [fileName]. */
    data class ChooseExportFile(val fileName: String) : BackupEvent

    data class Show(val message: BackupMessage) : BackupEvent
}

data class BackupUiState(
    val dialog: BackupDialog? = null,
    /** Exporting, reading or restoring; key derivation alone can take a second. */
    val isWorking: Boolean = false,
)

/** Export and restore from Settings (docs/spec.md, "Data safety"). */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val files: DocumentFiles,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val state = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = eventChannel.receiveAsFlow()

    /** Held only from the export dialog until the file is chosen, then wiped. */
    private var exportPassphrase: CharArray? = null

    /** A protected file waiting for its passphrase. */
    private var protectedFile: ByteArray? = null

    /** A read backup waiting for the user to confirm the restore. */
    private var readBackup: Backup? = null

    fun startExport() {
        state.update { it.copy(dialog = BackupDialog.ExportOptions) }
    }

    /** From the export dialog; null [passphrase] makes a plain file. */
    fun chooseExportFile(passphrase: CharArray?) {
        exportPassphrase = passphrase
        state.update { it.copy(dialog = null) }
        val today = timeSource.now().atZone(timeSource.zone()).toLocalDate()
        eventChannel.trySend(BackupEvent.ChooseExportFile("donsy-$today.json"))
    }

    /** The picker's answer; null when the user backed out. */
    fun exportTo(uri: Uri?) {
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri == null) {
            passphrase?.fill(Char(0))
            return
        }
        viewModelScope.launch {
            state.update { it.copy(isWorking = true) }
            val written = try {
                files.write(uri, backups.export(passphrase))
            } finally {
                passphrase?.fill(Char(0))
                state.update { it.copy(isWorking = false) }
            }
            show(if (written) BackupMessage.EXPORTED else BackupMessage.EXPORT_FAILED)
        }
    }

    /** The picker's answer; null when the user backed out. */
    fun restoreFrom(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            state.update { it.copy(isWorking = true) }
            val file = files.read(uri)
            if (file == null) {
                state.update { it.copy(isWorking = false) }
                show(BackupMessage.UNREADABLE)
            } else {
                protectedFile = file
                handle(backups.read(file, passphrase = null))
            }
        }
    }

    fun submitPassphrase(passphrase: CharArray) {
        val file = protectedFile ?: return
        viewModelScope.launch {
            state.update { it.copy(dialog = null, isWorking = true) }
            try {
                handle(backups.read(file, passphrase))
            } finally {
                passphrase.fill(Char(0))
            }
        }
    }

    private fun handle(result: BackupReadResult) {
        val dialog = when (result) {
            is BackupReadResult.Ready -> {
                readBackup = result.backup
                protectedFile = null
                val summary = result.backup.summary
                BackupDialog.ConfirmRestore(summary, summary.exportedAt.atZone(timeSource.zone()).toLocalDateTime())
            }
            BackupReadResult.NeedsPassphrase -> BackupDialog.Passphrase(wasWrong = false)
            BackupReadResult.WrongPassphrase -> BackupDialog.Passphrase(wasWrong = true)
            BackupReadResult.NotABackup -> null.also { failed(BackupMessage.NOT_A_BACKUP) }
            BackupReadResult.FromNewerVersion -> null.also { failed(BackupMessage.FROM_NEWER_VERSION) }
        }
        state.update { it.copy(dialog = dialog, isWorking = false) }
    }

    private fun failed(message: BackupMessage) {
        protectedFile = null
        show(message)
    }

    fun confirmRestore() {
        val backup = readBackup ?: return
        readBackup = null
        viewModelScope.launch {
            state.update { it.copy(dialog = null, isWorking = true) }
            val result = backups.restore(backup)
            state.update { it.copy(isWorking = false) }
            show(if (result == RestoreResult.Restored) BackupMessage.RESTORED else BackupMessage.FOCUS_SESSION_ACTIVE)
        }
    }

    /** Cancel in any dialog: nothing is kept. */
    fun dismissDialog() {
        exportPassphrase?.fill(Char(0))
        exportPassphrase = null
        protectedFile = null
        readBackup = null
        state.update { it.copy(dialog = null) }
    }

    private fun show(message: BackupMessage) {
        eventChannel.trySend(BackupEvent.Show(message))
    }
}
