// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.focus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.core.focus.FocusSessionManager
import be.kodeworks.donsy.domain.focus.RecoveryNote
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.feature.tasks.components.KindBadge
import be.kodeworks.donsy.feature.tasks.components.KindIcon
import be.kodeworks.donsy.feature.tasks.components.TaskMarker
import be.kodeworks.donsy.feature.tasks.components.TaskOrStepTitle
import be.kodeworks.donsy.feature.tasks.components.minutesText
import be.kodeworks.donsy.ui.components.DonsyCard
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.TabTopBar
import be.kodeworks.donsy.ui.components.scrollIndicators

@Composable
fun FocusScreen(
    onOpenSettings: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionAsked by viewModel.notificationPermissionAsked.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    // The notification permission is asked once, in context: when the first session starts.
    // The session starts whatever the answer is.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.start()
    }
    val start = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && !permissionAsked) {
            viewModel.markNotificationPermissionAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    Scaffold(topBar = { TabTopBar(stringResource(R.string.tab_focus), onOpenSettings) }) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .scrollIndicators(scrollState, ScrollIndicatorDefaults.colors())
                .verticalScroll(scrollState)
                // As on Tasks: bands, cards and buttons 4 dp from the edges, loose text at 16 dp (see TextInset).
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (val state = uiState) {
                FocusUiState.Loading -> Unit
                is FocusUiState.Setup -> Setup(state, viewModel, onStart = start)
                is FocusUiState.Active -> Running(state, viewModel)
                is FocusUiState.Ended -> Ended(state, viewModel)
            }
        }
    }
}

/** Lines loose text up with the text inside the heading bands and cards. */
private val TextInset = Modifier.padding(horizontal = 12.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(state: FocusUiState.Setup, viewModel: FocusViewModel, onStart: () -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.focus_setup_title), help = stringResource(R.string.focus_setup_help))
    DonsyCard(modifier = Modifier.fillMaxWidth()) {
        val target = state.target
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (target != null) {
                TaskOrStepTitle(target.title, taskTitle = target.partOf, step = target.step, titleStyle = MaterialTheme.typography.titleMedium)
            } else {
                Text(stringResource(R.string.focus_target_none), style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.options.isNotEmpty()) {
                    OutlinedButton(onClick = { choosing = true }) {
                        Text(stringResource(if (target == null) R.string.action_choose_task else R.string.action_change_task))
                    }
                }
                if (target != null) {
                    OutlinedButton(onClick = { viewModel.selectTarget(null) }) { Text(stringResource(R.string.action_clear_task)) }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.focus_duration_label), help = stringResource(R.string.focus_duration_help))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = TextInset) {
            // Keeps a length picked before Settings stopped offering it visible.
            (state.durations + state.minutes).distinct().sorted().forEach { minutes ->
                FilterChip(
                    selected = state.minutes == minutes,
                    onClick = { viewModel.selectMinutes(minutes) },
                    label = { Text(minutesText(minutes)) },
                )
            }
        }
    }

    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_start_focus))
    }

    if (state.offerPreciseAlerts) PreciseAlertsOffer(viewModel)

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.action_choose_task)) },
            text = {
                LazyColumn {
                    items(state.options, key = { it.target.taskId }) { option ->
                        ListItem(
                            overlineContent = option.partOf?.let { { Text(it) } },
                            headlineContent = { Text(option.title) },
                            leadingContent = { KindIcon(option.step) },
                            supportingContent = { KindBadge(option.step, Modifier.padding(top = 4.dp)) },
                            modifier = Modifier.clickable {
                                viewModel.selectTarget(option.target)
                                choosing = false
                            },
                        )
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Asked once, in context: on-time end alerts need Android's exact-alarm permission. */
@Composable
private fun PreciseAlertsOffer(viewModel: FocusViewModel) {
    val context = LocalContext.current
    DonsyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.focus_precise_offer_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.focus_precise_offer_body), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::declinePreciseAlerts) { Text(stringResource(R.string.action_no_thanks)) }
                Button(onClick = {
                    if (viewModel.acceptPreciseAlerts() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }) { Text(stringResource(R.string.action_allow)) }
            }
        }
    }
}

@Composable
private fun Running(state: FocusUiState.Active, viewModel: FocusViewModel) {
    val target = state.target
    if (target != null) {
        TaskOrStepTitle(
            target.title,
            taskTitle = target.partOf,
            step = target.step,
            titleStyle = MaterialTheme.typography.titleLarge,
            modifier = TextInset.semantics { heading() },
        )
    } else {
        Text(
            stringResource(R.string.focus_just_focusing),
            style = MaterialTheme.typography.titleLarge,
            modifier = TextInset.semantics { heading() },
        )
    }

    val minutesLeft = FocusSessionManager.roundedUpMinutes(state.remaining)
    val timeLeftDescription = pluralStringResource(R.plurals.focus_minutes_left, minutesLeft, minutesLeft)
    val pausedLabel = stringResource(R.string.focus_paused)
    val pausedDescription = stringResource(R.string.focus_paused_time_left, timeLeftDescription)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            // Read as "12 minutes left" (and "Paused"), not as digits every second.
            .clearAndSetSemantics {
                contentDescription = if (state.isPaused) pausedDescription else timeLeftDescription
            },
    ) {
        CircularProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.size(240.dp),
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DateUtils.formatElapsedTime(state.remaining.seconds), style = MaterialTheme.typography.displayLarge)
            if (state.isPaused) {
                Text(pausedLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (state.isPaused) {
        Button(onClick = viewModel::resume, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_resume)) }
    } else {
        Button(onClick = viewModel::pause, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_pause)) }
    }
    OutlinedButton(onClick = viewModel::finish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_finish_early)) }
    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel_session)) }
    Text(
        stringResource(R.string.focus_task_unchanged),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = TextInset,
    )
}

@Composable
private fun Ended(state: FocusUiState.Ended, viewModel: FocusViewModel) {
    val summary = state.summary
    var declined by rememberSaveable(summary.sessionId) { mutableStateOf(false) }
    val offerToCompleteTask by viewModel.offerToCompleteTask.collectAsStateWithLifecycle()

    Text(
        stringResource(if (summary.status == FocusSessionStatus.COMPLETED) R.string.focus_done_title else R.string.focus_ended_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = TextInset.semantics { heading() },
    )
    Text(
        pluralStringResource(R.plurals.focus_done_body, summary.focusedMinutes, summary.focusedMinutes),
        style = MaterialTheme.typography.bodyLarge,
        modifier = TextInset,
    )
    when (summary.note) {
        RecoveryNote.FINISHED_WHILE_AWAY -> Text(stringResource(R.string.focus_note_finished_away), style = MaterialTheme.typography.bodyMedium, modifier = TextInset)
        RecoveryNote.ENDED_AFTER_LONG_PAUSE -> Text(stringResource(R.string.focus_note_long_pause), style = MaterialTheme.typography.bodyMedium, modifier = TextInset)
        null -> Unit
    }

    val target = state.target
    if (target != null) {
        DonsyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    offerToCompleteTask -> {
                        Text(stringResource(R.string.focus_marked_done), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.last_step_title), style = MaterialTheme.typography.titleSmall)
                        // The task the question is about, marked like tasks elsewhere.
                        target.partOf?.let { taskTitle ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TaskMarker(size = 28.dp)
                                Text(taskTitle, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Text(stringResource(R.string.last_step_body), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::declineWholeTask) { Text(stringResource(R.string.action_not_yet)) }
                            Button(onClick = viewModel::completeWholeTask) { Text(stringResource(R.string.action_mark_done)) }
                        }
                    }
                    // From the stored state, so a step unchecked elsewhere is asked about again.
                    target.isDone -> Text(stringResource(R.string.focus_marked_done), style = MaterialTheme.typography.bodyLarge)
                    !declined -> {
                        Text(stringResource(R.string.focus_mark_done_question, target.title), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { declined = true }) { Text(stringResource(R.string.action_not_yet)) }
                            Button(onClick = viewModel::markTargetDone) { Text(stringResource(R.string.action_mark_done)) }
                        }
                    }
                    else -> Text(target.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    Button(onClick = viewModel::closeSummary, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_start_another))
    }
}
