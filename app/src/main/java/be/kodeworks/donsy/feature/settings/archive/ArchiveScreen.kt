// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.ui.components.BackTopBar
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.scrollIndicators
import be.kodeworks.donsy.ui.format.DateFormats

/** Done tasks that were archived, by hand or automatically. "Bring back" returns one to Done. */
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val broughtBackMessage = stringResource(R.string.archive_brought_back)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.broughtBack.collect { id ->
            val result = snackbarHostState.showSnackbar(broughtBackMessage, undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.archive(id)
        }
    }

    val listState = rememberLazyListState()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.archive_title), onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Unit
            uiState.tasks.isEmpty() -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.archive_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .scrollIndicators(listState, ScrollIndicatorDefaults.colors()),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.archive_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(uiState.tasks, key = { it.id }) { task -> ArchivedRow(task, onBringBack = { viewModel.bringBack(task.id) }) }
            }
        }
    }
}

@Composable
private fun ArchivedRow(task: ArchivedTask, onBringBack: () -> Unit) {
    val bringBackDescription = stringResource(R.string.action_bring_back_named, task.title)
    ListItem(
        headlineContent = { Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = task.doneOn?.let { date ->
            { Text(stringResource(R.string.archive_done_on, DateFormats.shortDate(date, task.doneThisYear))) }
        },
        trailingContent = {
            OutlinedButton(onClick = onBringBack, modifier = Modifier.semantics { contentDescription = bringBackDescription }) {
                Text(stringResource(R.string.action_bring_back))
            }
        },
    )
}
