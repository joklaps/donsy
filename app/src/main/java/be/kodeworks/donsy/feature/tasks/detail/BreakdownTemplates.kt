// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.breakdown.rememberBreakdownTemplates
import be.kodeworks.donsy.domain.breakdown.BreakdownEngine
import be.kodeworks.donsy.domain.breakdown.BreakdownTemplate
import be.kodeworks.donsy.ui.components.DonsyCard

/**
 * Built-in breakdown templates on the task detail screen: a suggestion while the task has no steps
 * and its title matches a template, otherwise a button that opens the template list. Either way the
 * steps are shown in an editable preview before anything is added.
 */
@Composable
fun BreakdownTemplates(task: TaskDetail, onAddSteps: (List<String>) -> Unit) {
    val templates = rememberBreakdownTemplates()
    val suggested = remember(task.title, task.notes, templates) { BreakdownEngine.suggest(task.title, task.notes, templates) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }

    val suggestion = suggested.firstOrNull()
    if (task.steps.isEmpty() && suggestion != null) {
        DonsyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.template_suggestion, suggestion.name), style = MaterialTheme.typography.bodyLarge)
                Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.action_other_templates)) }
                    Button(onClick = { previewId = suggestion.id }) { Text(stringResource(R.string.action_try_template_steps)) }
                }
            }
        }
    } else {
        OutlinedButton(onClick = { picking = true }) {
            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.action_add_template_steps))
        }
    }

    if (picking) {
        TemplatePickerDialog(
            // Matching templates first, then the rest in catalog order.
            templates = suggested + (templates - suggested.toSet()),
            matching = suggested.map { it.id }.toSet(),
            onPick = { template ->
                picking = false
                previewId = template.id
            },
            onDismiss = { picking = false },
        )
    }
    templates.firstOrNull { it.id == previewId }?.let { template ->
        TemplatePreviewDialog(
            template = template,
            existingSteps = task.steps.map { it.title },
            onAdd = { titles ->
                previewId = null
                onAddSteps(titles)
            },
            onDismiss = { previewId = null },
        )
    }
}

@Composable
private fun TemplatePickerDialog(
    templates: List<BreakdownTemplate>,
    matching: Set<String>,
    onPick: (BreakdownTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                templates.forEach { template ->
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = if (template.id in matching) {
                            { Text(stringResource(R.string.template_matches_task)) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onPick(template) },
                    )
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Every step can be unticked or reworded before it is added. Reordering happens in the step list afterwards. */
@Composable
private fun TemplatePreviewDialog(
    template: BreakdownTemplate,
    existingSteps: List<String>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val texts = rememberSaveable(template.id, saver = stateListSaver()) {
        BreakdownEngine.stepsFor(template, existingSteps).toMutableStateList()
    }
    val included = rememberSaveable(template.id, saver = stateListSaver()) {
        MutableList(texts.size) { true }.toMutableStateList()
    }
    val chosen = texts.filterIndexed { index, text -> included[index] && text.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (texts.isEmpty()) {
                    Text(stringResource(R.string.template_all_steps_present), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.template_preview_body), style = MaterialTheme.typography.bodyMedium)
                }
                texts.forEachIndexed { index, text ->
                    val includeLabel = stringResource(R.string.template_include_step, text)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = included[index],
                            onCheckedChange = { included[index] = it },
                            modifier = Modifier.semantics { contentDescription = includeLabel },
                        )
                        // Wraps so long steps stay readable; a step is still one line of text.
                        OutlinedTextField(
                            value = text,
                            onValueChange = { texts[index] = it.replace('\n', ' ') },
                            enabled = included[index],
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (texts.isEmpty()) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            } else {
                Button(onClick = { onAdd(chosen) }, enabled = chosen.isNotEmpty()) {
                    Text(pluralStringResource(R.plurals.action_add_template_steps_count, chosen.size, chosen.size))
                }
            }
        },
        dismissButton = if (texts.isEmpty()) {
            null
        } else {
            { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        },
    )
}

private fun <T : Any> stateListSaver() = listSaver<SnapshotStateList<T>, T>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)
