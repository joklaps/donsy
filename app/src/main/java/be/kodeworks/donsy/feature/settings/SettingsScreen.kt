// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings

import android.content.Intent
import android.icu.text.ListFormatter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.locale.AppLanguages
import be.kodeworks.donsy.domain.archive.AutoArchive
import be.kodeworks.donsy.domain.focus.FocusDurations
import be.kodeworks.donsy.feature.settings.backup.BackupDialog
import be.kodeworks.donsy.feature.settings.backup.BackupEvent
import be.kodeworks.donsy.feature.settings.backup.BackupMessage
import be.kodeworks.donsy.feature.settings.backup.BackupViewModel
import be.kodeworks.donsy.feature.settings.backup.ConfirmRestoreDialog
import be.kodeworks.donsy.feature.settings.backup.ExportDialog
import be.kodeworks.donsy.feature.settings.backup.PassphraseDialog
import be.kodeworks.donsy.feature.tasks.components.minutesText
import be.kodeworks.donsy.ui.components.BackTopBar
import be.kodeworks.donsy.ui.components.ListSectionHeaderPadding
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.scrollIndicators

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languageTags = remember(context) { AppLanguages.supportedTags(context) }
    val currentTag = remember { AppLanguages.currentTag() }
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    var choosingFocusLengths by rememberSaveable { mutableStateOf(false) }
    var choosingFocusDefault by rememberSaveable { mutableStateOf(false) }
    var choosingAutoArchive by rememberSaveable { mutableStateOf(false) }
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // The system file picker: no storage permission needed (docs/spec.md, "Data safety").
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) {
        backupViewModel.exportTo(it)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        backupViewModel.restoreFrom(it)
    }
    val messages = backupMessages()
    LaunchedEffect(backupViewModel) {
        backupViewModel.events.collect { event ->
            when (event) {
                is BackupEvent.ChooseExportFile -> exportLauncher.launch(event.fileName)
                is BackupEvent.Show -> snackbarHostState.showSnackbar(messages.getValue(event.message))
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .scrollIndicators(listState, ScrollIndicatorDefaults.colors()),
        ) {
            if (backupState.isWorking) item {
                val working = stringResource(R.string.backup_working)
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = working })
            }
            item { SectionHeader(stringResource(R.string.settings_section_general), ListSectionHeaderPadding) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = { Text(languageLabel(currentTag)) },
                    modifier = Modifier.clickable { choosingLanguage = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_focus_lengths)) },
                    supportingContent = { Text(minutesListText(uiState.focusDurations)) },
                    modifier = Modifier.clickable { choosingFocusLengths = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_focus_default)) },
                    supportingContent = { Text(minutesText(uiState.focusDefaultMinutes)) },
                    modifier = Modifier.clickable { choosingFocusDefault = true },
                )
            }
            // Before Android 12 exact alarms need no permission and are always used.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_precise_alerts)) },
                    supportingContent = { Text(stringResource(R.string.settings_precise_alerts_body)) },
                    trailingContent = { Switch(checked = uiState.preciseEndAlerts, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = uiState.preciseEndAlerts, role = Role.Switch) { enabled ->
                        if (viewModel.setPreciseEndAlerts(enabled)) {
                            // Android 12+: exact alarms need the user's permission, given in system settings.
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)),
                            )
                        }
                    },
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_data), ListSectionHeaderPadding) }
            item {
                Text(
                    stringResource(R.string.settings_data_local_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_export_body)) },
                    modifier = Modifier.clickable(enabled = !backupState.isWorking, onClick = backupViewModel::startExport),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_restore)) },
                    supportingContent = { Text(stringResource(R.string.settings_restore_body)) },
                    modifier = Modifier.clickable(enabled = !backupState.isWorking) { restoreLauncher.launch(BACKUP_OPEN_TYPES) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_recently_deleted)) },
                    supportingContent = { Text(stringResource(R.string.settings_recently_deleted_body)) },
                    modifier = Modifier.clickable(onClick = onOpenRecentlyDeleted),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_archive)) },
                    supportingContent = { Text(stringResource(R.string.settings_archive_body)) },
                    modifier = Modifier.clickable(onClick = onOpenArchive),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_auto_archive)) },
                    supportingContent = { Text(autoArchiveText(uiState.archiveAfterDays)) },
                    modifier = Modifier.clickable { choosingAutoArchive = true },
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_about), ListSectionHeaderPadding) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                    supportingContent = { Text(stringResource(R.string.settings_licenses_body)) },
                    modifier = Modifier.clickable(onClick = onOpenLicenses),
                )
            }
        }
    }

    when (val dialog = backupState.dialog) {
        BackupDialog.ExportOptions -> ExportDialog(onExport = backupViewModel::chooseExportFile, onDismiss = backupViewModel::dismissDialog)
        is BackupDialog.Passphrase -> PassphraseDialog(
            wasWrong = dialog.wasWrong,
            onSubmit = backupViewModel::submitPassphrase,
            onDismiss = backupViewModel::dismissDialog,
        )
        is BackupDialog.ConfirmRestore -> ConfirmRestoreDialog(
            summary = dialog.summary,
            exportedAt = dialog.exportedAt,
            onConfirm = backupViewModel::confirmRestore,
            onDismiss = backupViewModel::dismissDialog,
        )
        null -> Unit
    }
    if (choosingAutoArchive) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_auto_archive),
            options = AutoArchive.CHOICES,
            selected = uiState.archiveAfterDays,
            label = { autoArchiveText(it) },
            onSelect = { days ->
                choosingAutoArchive = false
                viewModel.setArchiveAfterDays(days)
            },
            onDismiss = { choosingAutoArchive = false },
        )
    }

    if (choosingLanguage) {
        // A null tag means "follow the system language".
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf<String?>(null) + languageTags,
            selected = languageTags.firstOrNull { it.equals(currentTag, ignoreCase = true) },
            label = { languageLabel(it) },
            onSelect = { tag ->
                choosingLanguage = false
                if (tag != currentTag) AppLanguages.apply(tag)
            },
            onDismiss = { choosingLanguage = false },
        )
    }
    if (choosingFocusLengths) {
        FocusLengthsDialog(
            offered = uiState.focusDurations,
            onToggle = viewModel::setFocusDurationOffered,
            onDismiss = { choosingFocusLengths = false },
        )
    }
    if (choosingFocusDefault) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_focus_default),
            options = uiState.focusDurations,
            selected = uiState.focusDefaultMinutes,
            label = { minutesText(it) },
            onSelect = { minutes ->
                choosingFocusDefault = false
                viewModel.setFocusDefaultMinutes(minutes)
            },
            onDismiss = { choosingFocusDefault = false },
        )
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Changes apply right away; the last offered length can't be unticked. */
@Composable
private fun FocusLengthsDialog(
    offered: List<Int>,
    onToggle: (minutes: Int, offered: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_focus_lengths)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_focus_lengths_body), style = MaterialTheme.typography.bodyMedium)
                FocusDurations.PRESETS.forEach { minutes ->
                    val checked = minutes in offered
                    val enabled = !(checked && offered.size == 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onToggle(minutes, it) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                        Text(minutesText(minutes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/** Exports are JSON; when restoring, some file managers label .json files as generic binary or text. */
private const val BACKUP_MIME_TYPE = "application/json"
private val BACKUP_OPEN_TYPES = arrayOf(BACKUP_MIME_TYPE, "application/octet-stream", "text/plain")

@Composable
private fun autoArchiveText(days: Int): String =
    if (days == AutoArchive.NEVER) {
        stringResource(R.string.settings_auto_archive_never)
    } else {
        pluralStringResource(R.plurals.settings_auto_archive_after, days, days)
    }

@Composable
private fun backupMessages(): Map<BackupMessage, String> = BackupMessage.entries.associateWith { message ->
    stringResource(
        when (message) {
            BackupMessage.EXPORTED -> R.string.backup_exported
            BackupMessage.EXPORT_FAILED -> R.string.backup_export_failed
            BackupMessage.RESTORED -> R.string.backup_restored
            BackupMessage.UNREADABLE -> R.string.backup_unreadable
            BackupMessage.NOT_A_BACKUP -> R.string.backup_not_a_backup
            BackupMessage.FROM_NEWER_VERSION -> R.string.backup_from_newer_version
            BackupMessage.FOCUS_SESSION_ACTIVE -> R.string.backup_focus_session_active
        },
    )
}

/** "5 min, 10 min and 15 min", joined the way the current language does it. */
@Composable
private fun minutesListText(minutes: List<Int>): String {
    val texts = minutes.map { minutesText(it) }
    val locale = LocalConfiguration.current.locales[0]
    return remember(texts, locale) { ListFormatter.getInstance(locale).format(texts) }
}

@Composable
private fun languageLabel(tag: String?): String =
    if (tag == null) stringResource(R.string.settings_language_system) else AppLanguages.nativeDisplayName(tag)
