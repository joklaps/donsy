// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.feature.tasks.components.CompleteAfterLastStepDialog
import be.kodeworks.donsy.feature.tasks.components.CompleteWithOpenStepsDialog
import be.kodeworks.donsy.feature.tasks.components.MetaLabel
import be.kodeworks.donsy.feature.tasks.components.TaskMetaIcons
import be.kodeworks.donsy.feature.tasks.components.effortText
import be.kodeworks.donsy.feature.tasks.components.estimateText
import be.kodeworks.donsy.feature.tasks.components.stepsProgressText
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.scrollIndicators
import be.kodeworks.donsy.ui.format.dueLabelText
import be.kodeworks.donsy.ui.format.repeatText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (taskId: Long) -> Unit,
    onDeleted: (taskId: Long) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var leaving by rememberSaveable { mutableStateOf(false) }
    val currentOnDeleted by rememberUpdatedState(onDeleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskDetailEvent.Deleted -> {
                    leaving = true
                    currentOnDeleted(event.taskId)
                }
            }
        }
    }
    // The task vanished some other way (e.g. a stale link): just go back.
    LaunchedEffect(uiState.isLoading, uiState.task == null) {
        if (!uiState.isLoading && uiState.task == null && !leaving) {
            leaving = true
            onNavigateBack()
        }
    }

    val task = uiState.task
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_navigate_back))
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { onEdit(task.id) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit_task))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            when (task.status) {
                                TaskStatus.ACTIVE -> DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_pause_task)) },
                                    onClick = { menuOpen = false; viewModel.pause() },
                                )
                                TaskStatus.PAUSED -> DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_resume_task)) },
                                    onClick = { menuOpen = false; viewModel.resume() },
                                )
                                else -> Unit
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menuOpen = false; viewModel.delete() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (task != null) {
            TaskDetailContent(
                task = task,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        }
    }

    when (val dialog = uiState.dialog) {
        is TaskDetailDialog.CompleteWithOpenSteps -> CompleteWithOpenStepsDialog(
            openSteps = dialog.openSteps,
            onConfirm = viewModel::confirmCompletion,
            onDismiss = viewModel::dismissDialog,
        )
        TaskDetailDialog.CompleteAfterLastStep -> CompleteAfterLastStepDialog(
            onConfirm = { viewModel.confirmCompletion(alsoCompleteOpenSteps = false) },
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDetailContent(task: TaskDetail, viewModel: TaskDetailViewModel, modifier: Modifier = Modifier) {
    var editingStep by rememberSaveable { mutableStateOf<Long?>(null) }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            // Shrinks the scroll area above the keyboard, so the focused "Add a step" field stays visible.
            .imePadding()
            .scrollIndicators(scrollState, ScrollIndicatorDefaults.colors())
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            task.title,
            style = MaterialTheme.typography.headlineSmall,
            textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
        )
        when {
            task.status == TaskStatus.PAUSED -> StatusNote(stringResource(R.string.task_status_paused))
            task.status == TaskStatus.DONE -> StatusNote(stringResource(R.string.task_status_done))
            task.doneToday -> StatusNote(stringResource(R.string.routine_status_done_today))
        }
        val due = task.due
        val routine = task.routine
        if (task.isImportant || due != null || task.estimateMinutes != null || task.effort != null || routine != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (routine != null) MetaLabel(TaskMetaIcons.repeat, repeatText(routine.days, routine.time))
                if (task.isImportant) MetaLabel(TaskMetaIcons.important, stringResource(R.string.task_important))
                if (due != null) MetaLabel(TaskMetaIcons.due, dueLabelText(due))
                task.estimateMinutes?.let { MetaLabel(TaskMetaIcons.estimate, estimateText(it)) }
                task.effort?.let { MetaLabel(TaskMetaIcons.effort, stringResource(R.string.task_effort_label, effortText(it))) }
            }
        }
        if (task.notes.isNotBlank()) {
            Text(task.notes, style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.task_steps_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (task.steps.isNotEmpty()) {
                Text(
                    stepsProgressText(task.stepsDone, task.steps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (task.steps.isEmpty()) {
            Text(
                stringResource(R.string.task_steps_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReorderableStepList(steps = task.steps, onMove = viewModel::moveStep) { step, index, drag ->
            StepRow(
                step = step,
                index = index,
                lastIndex = task.steps.lastIndex,
                drag = drag,
                onCheckedChange = { viewModel.setStepDone(step.id, it) },
                onEdit = { editingStep = step.id },
                onMove = viewModel::moveStep,
            )
        }
        AddStepField(onAdd = viewModel::addStep)
        if (task.status != TaskStatus.DONE) BreakdownTemplates(task, onAddSteps = viewModel::addSteps)

        if (routine != null) {
            // Only on one of its days, and only while active: a paused routine stays quiet.
            when {
                task.status != TaskStatus.ACTIVE || !routine.isToday -> Unit
                task.doneToday -> OutlinedButton(onClick = { viewModel.setDoneToday(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_routine_not_done))
                }
                else -> Button(onClick = { viewModel.setDoneToday(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_routine_done_today))
                }
            }
        } else if (task.status == TaskStatus.DONE) {
            OutlinedButton(onClick = viewModel::reopen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_reopen))
            }
        } else {
            Button(onClick = viewModel::complete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_mark_task_done))
            }
        }
    }

    task.steps.firstOrNull { it.id == editingStep }?.let { step ->
        EditStepDialog(
            initialTitle = step.title,
            onSave = { viewModel.renameStep(step.id, it); editingStep = null },
            onDelete = { viewModel.deleteStep(step.id); editingStep = null },
            onDismiss = { editingStep = null },
        )
    }
}

@Composable
private fun StatusNote(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StepRow(
    step: StepItem,
    index: Int,
    lastIndex: Int,
    drag: StepDragScope,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    Surface(
        tonalElevation = if (drag.isDragging) 4.dp else 0.dp,
        shadowElevation = if (drag.isDragging) 4.dp else 0.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Screen-reader alternative to dragging.
                .semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(moveUp) { onMove(index, index - 1); true })
                        if (index < lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(index, index + 1); true })
                    }
                },
        ) {
            Checkbox(checked = step.isDone, onCheckedChange = onCheckedChange)
            Text(
                step.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (step.isDone) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.action_edit_step), onClick = onEdit)
                    .padding(vertical = 12.dp),
            )
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.action_drag_step),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = drag.dragHandle
                    .padding(12.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
private fun AddStepField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.task_add_step_placeholder)) },
        singleLine = true,
        // Done adds the step and keeps the keyboard open for the next one.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = {
            IconButton(onClick = add, enabled = text.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_step))
            }
        },
    )
}

@Composable
private fun EditStepDialog(
    initialTitle: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_edit_step_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) onSave(title) }),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.action_delete_step)) }
        },
    )
}
