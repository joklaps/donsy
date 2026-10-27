// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.feature.braindump.capture.CaptureFab
import be.kodeworks.donsy.feature.braindump.capture.QuickCaptureSheet
import be.kodeworks.donsy.ui.components.ListSectionHeaderPadding
import be.kodeworks.donsy.ui.components.ScrollIndicatorDefaults
import be.kodeworks.donsy.ui.components.SectionHeader
import be.kodeworks.donsy.ui.components.TabTopBar
import be.kodeworks.donsy.ui.components.scrollIndicators
import be.kodeworks.donsy.ui.format.capturedAtLabel
import kotlinx.coroutines.launch

@Composable
fun BrainDumpScreen(
    onOpenSettings: () -> Unit,
    onMakeTask: (brainDumpItemId: Long) -> Unit,
    thoughtConverted: Boolean,
    onThoughtConvertedHandled: () -> Unit,
    viewModel: BrainDumpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var capturing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val deletedMessage = stringResource(R.string.braindump_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BrainDumpEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.id)
                }
            }
        }
    }

    val convertedMessage = stringResource(R.string.braindump_converted)
    val scope = rememberCoroutineScope()
    LaunchedEffect(thoughtConverted) {
        if (thoughtConverted) {
            // Clearing the result restarts this effect, so the snackbar runs in the screen's scope.
            onThoughtConvertedHandled()
            scope.launch { snackbarHostState.showSnackbar(convertedMessage) }
        }
    }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.tab_braindump), onOpenSettings) },
        floatingActionButton = {
            CaptureFab(
                onClick = {
                    // A new thought that doesn't match the search would seem to disappear.
                    viewModel.setQuery("")
                    capturing = true
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BrainDumpContent(
            uiState = uiState,
            onQueryChange = viewModel::setQuery,
            onToggleSortOrder = viewModel::toggleSortOrder,
            onItemClick = { editingId = it },
            onMakeTask = onMakeTask,
            modifier = Modifier.padding(padding),
        )
    }

    QuickCaptureSheet(visible = capturing, onDismiss = { capturing = false })

    val editing = uiState.items.firstOrNull { it.id == editingId }
    if (editing != null) {
        BrainDumpEditorSheet(
            title = stringResource(R.string.braindump_edit_title),
            initialText = editing.text,
            onSave = { viewModel.updateText(editing.id, it) },
            onDelete = { viewModel.delete(editing.id) },
            // Store edits first, so the task editor starts from the latest text.
            onMakeTask = { text -> viewModel.updateText(editing.id, text).invokeOnCompletion { onMakeTask(editing.id) } },
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun BrainDumpContent(
    uiState: BrainDumpUiState,
    onQueryChange: (String) -> Unit,
    onToggleSortOrder: () -> Unit,
    onItemClick: (Long) -> Unit,
    onMakeTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> Unit
        uiState.items.isEmpty() -> EmptyBrainDump(modifier)
        else -> Column(modifier.fillMaxSize()) {
            // Outside the list, so they stay in reach while scrolling.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // In line with the heading band below, like cards and bands on the other tabs.
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp)
                    // The field grows with large text; the button grows along.
                    .height(IntrinsicSize.Min),
            ) {
                SearchField(query = uiState.query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
                SortButton(newestFirst = uiState.newestFirst, onClick = onToggleSortOrder, modifier = Modifier.fillMaxHeight())
            }
            BrainDumpList(uiState, onItemClick, onMakeTask)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        // One line, so the field stays as tall as the sort button in every language.
        placeholder = { Text(stringResource(R.string.braindump_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.braindump_search_clear))
                }
            }
        },
        singleLine = true,
        // The list filters while typing; the Search key only closes the keyboard.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier,
    )
}

/**
 * Flips between newest and oldest first. Icon only, to leave the search field room; long-press
 * shows the current order as a tooltip, and screen readers hear it as the button's state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortButton(newestFirst: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.braindump_sort_description)
    val order = stringResource(if (newestFirst) R.string.braindump_sort_newest else R.string.braindump_sort_oldest)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(order) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        OutlinedIconButton(
            onClick = onClick,
            // The size, shape and border colour of the search field beside it.
            shape = OutlinedTextFieldDefaults.shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .width(56.dp)
                .heightIn(min = 56.dp)
                .fillMaxHeight()
                .semantics {
                    contentDescription = description
                    stateDescription = order
                },
        ) {
            // As in spreadsheets: down for descending (newest first), up for ascending (oldest first).
            Icon(if (newestFirst) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, contentDescription = null)
        }
    }
}

@Composable
private fun BrainDumpList(
    uiState: BrainDumpUiState,
    onItemClick: (Long) -> Unit,
    onMakeTask: (Long) -> Unit,
) {
    val searching = uiState.query.isNotBlank()
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicators(listState, ScrollIndicatorDefaults.colors()),
        // Keeps the last item clear of the floating capture button.
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item(key = "header") {
            SectionHeader(
                stringResource(R.string.braindump_section_thoughts),
                ListSectionHeaderPadding,
                help = stringResource(R.string.braindump_intro),
            )
        }
        if (searching && uiState.visibleItems.isEmpty()) {
            item(key = "no-match") {
                Text(
                    stringResource(R.string.braindump_search_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        }
        itemsIndexed(uiState.visibleItems, key = { _, item -> item.id }) { index, item ->
            ListItem(
                // Every other row on a soft grey, lighter than the heading band, so rows are easy to tell apart.
                colors = ListItemDefaults.colors(
                    containerColor = if (index % 2 == 1) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                ),
                headlineContent = {
                    Text(item.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                },
                // The button shares the date's line, so the thought's text gets the full width.
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(capturedAtLabel(item.capturedAt), modifier = Modifier.weight(1f))
                        MakeTaskButton(firstLine = item.text.lineSequence().first(), onClick = { onMakeTask(item.id) })
                    }
                },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.action_edit),
                    onClick = { onItemClick(item.id) },
                ),
            )
        }
    }
}

/**
 * One-tap shortcut to the task editor, prefilled with the thought (same as the edit sheet's button).
 * Icon only: a thought (lightbulb) becomes a task (check). Long-press shows the label as a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MakeTaskButton(firstLine: String, onClick: () -> Unit) {
    val label = stringResource(R.string.action_make_task)
    // Every row has this button, so screen readers hear which thought it is for.
    val description = stringResource(R.string.action_make_task_named, firstLine)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = Color.Transparent,
            // The visible pill is small; the touch target stays at least 48 dp.
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val tint = MaterialTheme.colorScheme.primary
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyBrainDump(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.braindump_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.braindump_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
