// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.deleted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
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
import be.kodeworks.donsy.ui.components.ListSectionHeaderPadding
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.scrollIndicators

/** Tasks and thoughts deleted in the last 7 days: restore them, or remove them for good. */
@Composable
fun RecentlyDeletedScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecentlyDeletedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Kind and id of the item waiting for "Delete for good?"; saved as plain values.
    var confirmKind by rememberSaveable { mutableStateOf<DeletedKind?>(null) }
    var confirmId by rememberSaveable { mutableStateOf<Long?>(null) }

    val restoredTask = stringResource(R.string.recently_deleted_restored_task)
    val restoredThought = stringResource(R.string.recently_deleted_restored_thought)
    LaunchedEffect(viewModel) {
        viewModel.restored.collect { kind ->
            snackbarHostState.showSnackbar(if (kind == DeletedKind.TASK) restoredTask else restoredThought)
        }
    }

    val listState = rememberLazyListState()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.recently_deleted_title), onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Unit
            uiState.isEmpty -> EmptyMessage(stringResource(R.string.recently_deleted_empty), Modifier.padding(padding))
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
                        stringResource(R.string.recently_deleted_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                section(R.string.recently_deleted_section_tasks, DeletedKind.TASK, uiState.tasks, viewModel::restore) { kind, id ->
                    confirmKind = kind
                    confirmId = id
                }
                section(R.string.recently_deleted_section_thoughts, DeletedKind.THOUGHT, uiState.thoughts, viewModel::restore) { kind, id ->
                    confirmKind = kind
                    confirmId = id
                }
            }
        }
    }

    val kind = confirmKind
    val entry = when (kind) {
        DeletedKind.TASK -> uiState.tasks
        DeletedKind.THOUGHT -> uiState.thoughts
        null -> emptyList()
    }.firstOrNull { it.id == confirmId }
    if (kind != null && entry != null) {
        val dismiss = {
            confirmKind = null
            confirmId = null
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(stringResource(R.string.delete_forever_body, entry.text.lineSequence().first())) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteForever(kind, entry.id)
                    dismiss()
                }) { Text(stringResource(R.string.action_delete_forever)) }
            },
            dismissButton = { OutlinedButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    kind: DeletedKind,
    entries: List<DeletedEntry>,
    onRestore: (DeletedKind, Long) -> Unit,
    onDeleteForever: (DeletedKind, Long) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "header-$kind") { SectionHeader(stringResource(titleRes), ListSectionHeaderPadding) }
    items(entries, key = { "$kind-${it.id}" }) { entry ->
        DeletedRow(entry, onRestore = { onRestore(kind, entry.id) }, onDeleteForever = { onDeleteForever(kind, entry.id) })
    }
}

@Composable
private fun DeletedRow(entry: DeletedEntry, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    val name = entry.text.lineSequence().first()
    val restoreDescription = stringResource(R.string.action_restore_named, name)
    ListItem(
        headlineContent = { Text(entry.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(pluralStringResource(R.plurals.recently_deleted_days_left, entry.daysLeft, entry.daysLeft)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.semantics { contentDescription = restoreDescription }) {
                    Text(stringResource(R.string.action_restore))
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.action_delete_forever_named, name))
                }
            }
        },
    )
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
