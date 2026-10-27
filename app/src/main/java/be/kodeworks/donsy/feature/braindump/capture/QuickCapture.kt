// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump.capture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.kodeworks.donsy.R
import be.kodeworks.donsy.feature.braindump.BrainDumpEditorSheet

@Composable
fun CaptureFab(onClick: () -> Unit) {
    val label = stringResource(R.string.action_capture)
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(label) },
        // The extended FAB hides its label from accessibility services, so name the button explicitly.
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** Shows the capture sheet while [visible]; [onSaved] runs after a thought was stored. */
@Composable
fun QuickCaptureSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val showVoiceHint by viewModel.showVoiceHint.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { currentOnSaved() }
    }

    if (visible) {
        BrainDumpEditorSheet(
            title = stringResource(R.string.braindump_capture_title),
            onSave = viewModel::capture,
            onDismiss = onDismiss,
            showVoiceHint = showVoiceHint,
        )
    }
}
