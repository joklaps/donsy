// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.recommendation.Starter
import be.kodeworks.donsy.feature.tasks.components.EstimateChoices
import be.kodeworks.donsy.feature.tasks.components.TaskOrStepTitle
import be.kodeworks.donsy.feature.tasks.components.estimateText
import be.kodeworks.donsy.ui.components.DonsyCard
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.TabTopBar
import be.kodeworks.donsy.ui.components.scrollIndicators
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onStartFocus: () -> Unit,
    onChooseTask: () -> Unit,
    onSortBrainDump: () -> Unit,
    onNewTask: () -> Unit,
    deletedTaskId: Long?,
    onDeletedTaskHandled: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val setAsideMessage = stringResource(R.string.home_set_aside)
    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.SetAside -> scope.launch {
                    val result = snackbarHostState.showSnackbar(setAsideMessage, undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoNotNow(event.taskId)
                }
            }
        }
    }
    LaunchedEffect(deletedTaskId) {
        val id = deletedTaskId ?: return@LaunchedEffect
        // Clearing the result restarts this effect, so the snackbar runs in the screen's scope.
        onDeletedTaskHandled()
        scope.launch {
            val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreTask(id)
        }
    }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.tab_home), onOpenSettings) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .scrollIndicators(scrollState, ScrollIndicatorDefaults.colors())
                .verticalScroll(scrollState)
                // As on Tasks: bands and cards 4 dp from the edges, text lined up at 16 dp.
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val content = uiState.content) {
                HomeContent.Loading -> Unit
                HomeContent.Welcome -> Welcome()
                is HomeContent.Suggestion -> {
                    RightNowChoices(uiState, viewModel)
                    SuggestionCard(
                        suggestion = content,
                        onStart = {
                            viewModel.startFocus()
                            onStartFocus()
                        },
                        onShowAnother = viewModel::showAnother,
                        onNotNow = viewModel::notNow,
                    )
                    OutlinedButton(onClick = onChooseTask, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_choose_myself))
                    }
                }
                is HomeContent.NothingToSuggest -> {
                    if (content.allSkipped) RightNowChoices(uiState, viewModel)
                    NothingToSuggest(
                        content = content,
                        onStartOver = viewModel::startOver,
                        onStarter = { starter ->
                            when (starter) {
                                Starter.SORT_BRAIN_DUMP -> onSortBrainDump()
                                Starter.ADD_SMALL_TASK -> onNewTask()
                                Starter.PICK_ANY_TASK -> onChooseTask()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Optional, per-visit context for the suggestion: available time and energy. */
@Composable
private fun RightNowChoices(uiState: HomeUiState, viewModel: HomeViewModel) {
    ChoiceChips(
        label = stringResource(R.string.home_time_label),
        help = stringResource(R.string.home_time_help),
        choices = EstimateChoices,
        selected = uiState.availableMinutes,
        onToggle = viewModel::toggleAvailableMinutes,
        text = { estimateText(it) },
    )
    ChoiceChips(
        label = stringResource(R.string.home_energy_label),
        help = stringResource(R.string.home_energy_help),
        choices = EnergyLevel.entries,
        selected = uiState.energy,
        onToggle = viewModel::toggleEnergy,
        text = { energyText(it) },
    )
}

/** Chips where tapping the selected one again clears the choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    label: String,
    help: String,
    choices: List<T>,
    selected: T?,
    onToggle: (T) -> Unit,
    text: @Composable (T) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(label, help = help)
        // Lines the chips up with the heading's text.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onToggle(choice) },
                    label = { Text(text(choice)) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: HomeContent.Suggestion,
    onStart: () -> Unit,
    onShowAnother: () -> Unit,
    onNotNow: () -> Unit,
) {
    DonsyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_suggestion_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            TaskOrStepTitle(
                title = suggestion.stepTitle ?: suggestion.taskTitle,
                taskTitle = suggestion.taskTitle.takeIf { suggestion.stepTitle != null },
                step = suggestion.stepPosition,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            suggestion.reasons.forEach { reason ->
                Text(reasonText(reason), style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onStart, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                Text(stringResource(R.string.action_start))
            }
            FlowRow(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onShowAnother) { Text(stringResource(R.string.action_show_another)) }
                OutlinedButton(onClick = onNotNow) { Text(stringResource(R.string.action_not_now)) }
            }
        }
    }
}

@Composable
private fun NothingToSuggest(
    content: HomeContent.NothingToSuggest,
    onStartOver: () -> Unit,
    onStarter: (Starter) -> Unit,
) {
    DonsyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (content.allSkipped) R.string.home_all_skipped_title else R.string.home_nothing_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (content.allSkipped) R.string.home_all_skipped_body else R.string.home_nothing_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (content.allSkipped) {
                Button(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_start_over))
                }
            }
            content.starters.forEach { starter ->
                OutlinedButton(onClick = { onStarter(starter) }, modifier = Modifier.fillMaxWidth()) {
                    Text(starterText(starter))
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.home_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
