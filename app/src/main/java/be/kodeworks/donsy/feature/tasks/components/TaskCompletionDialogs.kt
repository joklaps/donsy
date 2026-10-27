// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import be.kodeworks.donsy.R

/** Asked when completing a task that still has open steps. Nothing is checked off without asking. */
@Composable
fun CompleteWithOpenStepsDialog(
    openSteps: Int,
    onConfirm: (alsoCompleteOpenSteps: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.complete_open_steps_title)) },
        text = { Text(pluralStringResource(R.plurals.complete_open_steps_body, openSteps, openSteps)) },
        confirmButton = {
            Button(onClick = { onConfirm(true) }) { Text(stringResource(R.string.action_mark_steps_done)) }
        },
        dismissButton = {
            OutlinedButton(onClick = { onConfirm(false) }) { Text(stringResource(R.string.action_only_task)) }
        },
    )
}

/** Offered after the last open step is checked off; the task never completes by itself. */
@Composable
fun CompleteAfterLastStepDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.last_step_title)) },
        text = { Text(stringResource(R.string.last_step_body)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.action_mark_done)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_yet)) } },
    )
}
