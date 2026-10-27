// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.feature.tasks.components.CompleteWithOpenStepsDialog
import be.kodeworks.donsy.feature.tasks.components.MetaLabel
import be.kodeworks.donsy.feature.tasks.components.ReminderPermissionCard
import be.kodeworks.donsy.feature.tasks.components.TaskMetaIcons
import be.kodeworks.donsy.feature.tasks.components.stepsProgressText
import be.kodeworks.donsy.ui.components.ListSectionHeaderPadding
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.TabTopBar
import be.kodeworks.donsy.ui.components.scrollIndicators
import be.kodeworks.donsy.ui.format.dueLabelText
import be.kodeworks.donsy.ui.format.repeatText
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(
    onOpenSettings: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onNewTask: () -> Unit,
    deletedTaskId: Long?,
    onDeletedTaskHandled: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val scope = rememberCoroutineScope()
    LaunchedEffect(deletedTaskId) {
        val id = deletedTaskId ?: return@LaunchedEffect
        // Clearing the result restarts this effect, so the snackbar runs in the screen's scope.
        onDeletedTaskHandled()
        scope.launch {
            val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(id)
        }
    }

    val archivedMessage = stringResource(R.string.task_archived)
    LaunchedEffect(viewModel) {
        viewModel.archived.collect { id ->
            val result = snackbarHostState.showSnackbar(archivedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.unarchive(id)
        }
    }

    // A list keeps its first visible item in place when items are added above it, so a section that
    // newly appears at the top (e.g. Routines after making the first routine) would sit out of view.
    val listState = rememberLazyListState()
    val firstSection = uiState.firstSection
    var shownFirstSection by rememberSaveable { mutableStateOf(firstSection) }
    LaunchedEffect(firstSection) {
        if (firstSection != null && firstSection != shownFirstSection) {
            if (shownFirstSection != null) listState.scrollToItem(0)
            shownFirstSection = firstSection
        }
    }

    val newTaskLabel = stringResource(R.string.action_new_task)
    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.tab_tasks), onOpenSettings) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(newTaskLabel) },
                // The extended FAB hides its label from accessibility services, so name the button explicitly.
                modifier = Modifier.semantics { contentDescription = newTaskLabel },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Unit
            uiState.isEmpty -> EmptyTasks(Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .scrollIndicators(listState, ScrollIndicatorDefaults.colors()),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                if (uiState.routines.isNotEmpty()) {
                    item(key = "header-routines") {
                        SectionHeader(
                            stringResource(R.string.tasks_section_routines),
                            ListSectionHeaderPadding,
                            help = stringResource(R.string.tasks_section_routines_help),
                        )
                    }
                    uiState.remindersMissing?.let { missing ->
                        item(key = "reminders-off") { RemindersOff(missing, viewModel) }
                    }
                    items(uiState.routines, key = { it.id }) { TaskRow(it, onOpenTask, viewModel::toggleDone) }
                }
                taskSection(R.string.tasks_section_todo, R.string.tasks_section_todo_help, uiState.active, onOpenTask, viewModel::toggleDone)
                taskSection(R.string.tasks_section_paused, R.string.tasks_section_paused_help, uiState.paused, onOpenTask, viewModel::toggleDone)
                if (uiState.done.isNotEmpty()) {
                    item(key = "done-header") {
                        DoneHeader(count = uiState.done.size, expanded = uiState.showDone, onToggle = viewModel::toggleShowDone)
                    }
                    if (uiState.showDone) {
                        items(uiState.done, key = { it.id }) { TaskRow(it, onOpenTask, viewModel::toggleDone, onArchive = viewModel::archive) }
                    }
                }
            }
        }
    }

    uiState.pendingCompletion?.let { pending ->
        CompleteWithOpenStepsDialog(
            openSteps = pending.openSteps,
            onConfirm = viewModel::confirmCompletion,
            onDismiss = viewModel::dismissCompletion,
        )
    }
}

private fun LazyListScope.taskSection(
    titleRes: Int,
    helpRes: Int,
    tasks: List<TaskListItem>,
    onOpenTask: (Long) -> Unit,
    onToggleDone: (Long) -> Unit,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$titleRes") { SectionHeader(stringResource(titleRes), ListSectionHeaderPadding, help = stringResource(helpRes)) }
    items(tasks, key = { it.id }) { TaskRow(it, onOpenTask, onToggleDone) }
}

@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    SectionHeader(
        text = stringResource(R.string.tasks_section_done, count),
        onClick = onToggle,
        modifier = ListSectionHeaderPadding.semantics { stateDescription = state },
    ) {
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun RemindersOff(missing: MissingReminderPermission, viewModel: TasksViewModel) {
    val askedBefore by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle(initialValue = true)
    ReminderPermissionCard(
        missing = missing,
        message = stringResource(R.string.reminders_off_body),
        notificationsAskedBefore = askedBefore,
        onNotificationsAsked = viewModel::markNotificationPermissionAsked,
        onResult = viewModel::refreshPermissions,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    item: TaskListItem,
    onOpenTask: (Long) -> Unit,
    onToggleDone: (Long) -> Unit,
    /** Done tasks only: puts the task away in the archive. */
    onArchive: ((Long) -> Unit)? = null,
) {
    val routine = item.routine
    val toggleLabel = stringResource(
        when {
            routine != null && item.isDone -> R.string.action_routine_undo_named
            routine != null -> R.string.action_routine_done_named
            item.isDone -> R.string.action_reopen_task_named
            else -> R.string.action_complete_task_named
        },
        item.title,
    )
    ListItem(
        leadingContent = {
            if (routine == null || routine.isToday) {
                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = { onToggleDone(item.id) },
                    modifier = Modifier.semantics { contentDescription = toggleLabel },
                )
            } else {
                // Not one of its days: nothing to tick off today.
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(TaskMetaIcons.repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        headlineContent = {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = {
            val due = item.due
            if (item.isImportant || item.stepsTotal > 0 || due != null || routine != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (routine != null) {
                        MetaLabel(TaskMetaIcons.repeat, repeatText(routine.days, routine.time))
                        if (item.isDone) MetaLabel(TaskMetaIcons.doneToday, stringResource(R.string.routine_status_done_today))
                    }
                    if (item.isImportant) MetaLabel(TaskMetaIcons.important, stringResource(R.string.task_important))
                    if (due != null && !item.isDone) MetaLabel(TaskMetaIcons.due, dueLabelText(due))
                    if (item.stepsTotal > 0) MetaLabel(TaskMetaIcons.steps, stepsProgressText(item.stepsDone, item.stepsTotal))
                }
            }
        },
        trailingContent = onArchive?.let { archive ->
            {
                IconButton(onClick = { archive(item.id) }) {
                    Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.action_archive_named, item.title))
                }
            }
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.action_open_task)) { onOpenTask(item.id) },
    )
}

@Composable
private fun EmptyTasks(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.tasks_empty_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.tasks_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
