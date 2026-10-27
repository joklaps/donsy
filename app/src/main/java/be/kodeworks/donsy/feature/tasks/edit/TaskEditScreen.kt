// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.edit

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.feature.tasks.components.EstimateChoices
import be.kodeworks.donsy.feature.tasks.components.ReminderPermissionCard
import be.kodeworks.donsy.feature.tasks.components.effortText
import be.kodeworks.donsy.feature.tasks.components.estimateText
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.scrollIndicators
import be.kodeworks.donsy.ui.format.DateFormats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onClose: () -> Unit,
    onSaved: (TaskEditResult) -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel) {
        viewModel.results.collect {
            // Otherwise the keyboard stays up on the screen we return to.
            keyboard?.hide()
            currentOnSaved(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (uiState.mode) {
                                TaskEditMode.NEW -> R.string.task_edit_new_title
                                TaskEditMode.EDIT -> R.string.task_edit_title
                                TaskEditMode.FROM_BRAIN_DUMP -> R.string.task_edit_from_thought_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { keyboard?.hide(); onClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = uiState.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (!uiState.isLoading) {
            TaskForm(uiState, viewModel, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskForm(uiState: TaskEditUiState, viewModel: TaskEditViewModel, modifier: Modifier = Modifier) {
    val titleFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .scrollIndicators(scrollState, ScrollIndicatorDefaults.colors())
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedTextField(
            value = uiState.title,
            onValueChange = viewModel::setTitle,
            label = { Text(stringResource(R.string.task_field_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocus),
        )
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::setNotes,
            label = { Text(stringResource(R.string.task_field_notes)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true),
            modifier = Modifier.fillMaxWidth(),
        )

        RepeatField(uiState, viewModel)
        if (uiState.isRoutine) {
            RemindAtField(uiState.dueTime, viewModel::setDueTime)
            uiState.missingPermission?.let { missing ->
                val askedBefore by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle(initialValue = true)
                ReminderPermissionCard(
                    missing = missing,
                    message = stringResource(R.string.reminders_permission_body),
                    notificationsAskedBefore = askedBefore,
                    onNotificationsAsked = viewModel::markNotificationPermissionAsked,
                    onResult = viewModel::refreshPermissions,
                )
            }
        } else {
            DueDateField(uiState.dueDate, uiState.dueTime, viewModel::setDueDate, viewModel::setDueTime)
            OrdinaryTaskFields(uiState, viewModel)
        }
    }

    LaunchedEffect(Unit) {
        if (uiState.mode == TaskEditMode.NEW) titleFocus.requestFocus()
    }
}

/** Estimate, effort and importance only feed suggestions, which never include routines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrdinaryTaskFields(uiState: TaskEditUiState, viewModel: TaskEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        FormSection(stringResource(R.string.task_field_estimate), help = stringResource(R.string.task_field_estimate_help)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstimateChoices.forEach { minutes ->
                    FilterChip(
                        selected = uiState.estimateMinutes == minutes,
                        onClick = { viewModel.toggleEstimate(minutes) },
                        label = { Text(estimateText(minutes)) },
                    )
                }
            }
        }

        FormSection(stringResource(R.string.task_field_effort), help = stringResource(R.string.task_field_effort_help)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Effort.entries.forEach { effort ->
                    FilterChip(
                        selected = uiState.effort == effort,
                        onClick = { viewModel.toggleEffort(effort) },
                        label = { Text(effortText(effort)) },
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = uiState.isImportant, role = Role.Switch, onValueChange = viewModel::setImportant),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.task_field_important), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.task_field_important_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = uiState.isImportant, onCheckedChange = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatField(uiState: TaskEditUiState, viewModel: TaskEditViewModel) {
    FormSection(stringResource(R.string.task_field_repeat), help = stringResource(R.string.task_field_repeat_help)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatChoice.entries.forEach { choice ->
                FilterChip(
                    selected = uiState.repeat == choice,
                    onClick = { viewModel.setRepeat(choice) },
                    label = { Text(repeatChoiceText(choice)) },
                )
            }
        }
        if (uiState.repeat == RepeatChoice.PICK_DAYS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateFormats.weekInLocaleOrder().forEach { day ->
                    FilterChip(
                        selected = day in uiState.pickedDays,
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(DateFormats.shortWeekday(day)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun repeatChoiceText(choice: RepeatChoice): String = stringResource(
    when (choice) {
        RepeatChoice.NEVER -> R.string.repeat_never
        RepeatChoice.EVERY_DAY -> R.string.repeat_every_day
        RepeatChoice.WEEKDAYS -> R.string.repeat_weekdays
        RepeatChoice.PICK_DAYS -> R.string.repeat_pick_days
    },
)

/** A routine's reminder time. There is no default: an unwanted alarm at a made-up time helps nobody. */
@Composable
private fun RemindAtField(time: LocalTime?, onChange: (LocalTime) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FormSection(stringResource(R.string.task_field_remind_at)) {
        OutlinedButton(onClick = { picking = true }) {
            Text(if (time == null) stringResource(R.string.task_remind_time_none) else DateFormats.time(time))
        }
    }
    if (picking) {
        PickTimeDialog(initial = time ?: LocalTime.of(8, 0), onPicked = { onChange(it); picking = false }, onDismiss = { picking = false })
    }
}

/**
 * A form heading, lighter than the grey bands of the lists. With [help] it ends in the same ?
 * as those bands: tapping the heading shows or hides that short line under it.
 */
@Composable
private fun FormSection(title: String, help: String? = null, content: @Composable () -> Unit) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (showHelp) R.string.state_expanded else R.string.state_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            // Every heading is a 48 dp row, with or without ?, so the sections are spaced alike.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (help == null) Modifier else Modifier.clickable { showHelp = !showHelp })
                    .semantics(mergeDescendants = true) {
                        heading()
                        if (help != null) stateDescription = state
                    },
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (help != null) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.action_show_help),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (help != null && showHelp) {
                Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateField(
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FormSection(stringResource(R.string.task_field_due)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }) {
                Text(
                    if (dueDate == null) {
                        stringResource(R.string.task_due_none)
                    } else {
                        DateFormats.shortDate(dueDate, isThisYear = dueDate.year == LocalDate.now().year)
                    },
                )
            }
            if (dueDate != null) {
                OutlinedButton(onClick = { onChange(null) }) { Text(stringResource(R.string.action_remove_date)) }
            }
        }
        if (dueDate != null) DueTimeRow(dueTime, onTimeChange)
    }
    if (picking) {
        // The date picker works in UTC milliseconds at midnight.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                Button(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { OutlinedButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Without a chosen time a task is due by the end of its day, so the default reads "Before midnight". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueTimeRow(dueTime: LocalTime?, onChange: (LocalTime?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }) {
            Text(if (dueTime == null) stringResource(R.string.task_due_time_none) else DateFormats.time(dueTime))
        }
        if (dueTime != null) {
            OutlinedButton(onClick = { onChange(null) }) { Text(stringResource(R.string.action_remove_time)) }
        }
    }
    if (picking) {
        PickTimeDialog(initial = dueTime ?: LocalTime.of(23, 59), onPicked = { onChange(it); picking = false }, onDismiss = { picking = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickTimeDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) }
        },
        title = { TimePickerDialogDefaults.Title(displayMode) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = displayMode,
            )
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) TimePicker(state = state) else TimeInput(state = state)
    }
}
