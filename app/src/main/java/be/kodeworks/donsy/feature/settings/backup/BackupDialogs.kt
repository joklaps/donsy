// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.backup.BackupSummary
import be.kodeworks.donsy.ui.format.DateFormats
import java.time.LocalDateTime

/** A passphrase this short is too easy to guess to be worth the risk of forgetting it. */
private const val MIN_PASSPHRASE_LENGTH = 8

/**
 * Plain file by default, with a clear warning; a passphrase is optional (docs/spec.md, "Data safety").
 * Passphrases live only in this dialog's memory, never in saved state.
 */
@Composable
fun ExportDialog(onExport: (passphrase: CharArray?) -> Unit, onDismiss: () -> Unit) {
    var protect by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val mismatch = repeated.isNotEmpty() && repeated != passphrase
    val canExport = !protect || (passphrase.length >= MIN_PASSPHRASE_LENGTH && repeated == passphrase)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = protect, role = Role.Switch) { protect = it },
                ) {
                    Text(stringResource(R.string.export_protect), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = protect, onCheckedChange = null)
                }
                if (protect) {
                    PassphraseField(passphrase, { passphrase = it }, stringResource(R.string.export_passphrase))
                    PassphraseField(
                        repeated,
                        { repeated = it },
                        stringResource(R.string.export_passphrase_repeat),
                        error = stringResource(R.string.export_passphrase_mismatch).takeIf { mismatch },
                    )
                    Text(
                        stringResource(R.string.export_passphrase_rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.export_plain_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(if (protect) passphrase.toCharArray() else null) }, enabled = canExport) {
                Text(stringResource(R.string.action_export))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun PassphraseDialog(wasWrong: Boolean, onSubmit: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.restore_passphrase_body), style = MaterialTheme.typography.bodyMedium)
                PassphraseField(
                    passphrase,
                    { passphrase = it },
                    stringResource(R.string.export_passphrase),
                    error = stringResource(R.string.restore_passphrase_wrong).takeIf { wasWrong },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(passphrase.toCharArray()) }, enabled = passphrase.isNotEmpty()) {
                Text(stringResource(R.string.action_open_file))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** What the file holds, then a plain warning: a restore replaces everything. */
@Composable
fun ConfirmRestoreDialog(summary: BackupSummary, exportedAt: LocalDateTime, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.restore_confirm_made,
                        DateFormats.shortDate(exportedAt.toLocalDate(), isThisYear = false),
                        DateFormats.time(exportedAt.toLocalTime()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.restore_confirm_contains), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.fillMaxWidth()) {
                    CountLine(R.plurals.restore_count_tasks, summary.tasks)
                    CountLine(R.plurals.restore_count_routines, summary.routines)
                    CountLine(R.plurals.restore_count_thoughts, summary.notes)
                    CountLine(R.plurals.restore_count_focus_sessions, summary.focusSessions)
                    if (summary.recentlyDeleted > 0) CountLine(R.plurals.restore_count_recently_deleted, summary.recentlyDeleted)
                }
                Text(stringResource(R.string.restore_confirm_warning), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.action_replace_everything)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CountLine(pluralRes: Int, count: Int) {
    Text(stringResource(R.string.list_bullet, pluralStringResource(pluralRes, count, count)), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PassphraseField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
