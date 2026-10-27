// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import be.kodeworks.donsy.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** For bands in a list: 4 dp in from the edges, so their text lines up with the list items' text. */
val ListSectionHeaderPadding = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 4.dp)

/**
 * A section heading on a full-width band in a soft neutral grey: clearly a heading, without
 * colour (see the calm-UI rule in docs). With [onClick] the whole band is the touch target.
 *
 * With [help], the band ends in a ? and tapping it shows or hides that short text under the band.
 * The whole band is the touch target, so it stays as tall as bands without help.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    if (help == null) {
        Band(text, modifier, onClick, trailing)
        return
    }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (showHelp) R.string.state_expanded else R.string.state_collapsed)
    val helpLabel = stringResource(R.string.action_show_help)
    Column(modifier) {
        Band(
            text = text,
            modifier = Modifier.semantics { stateDescription = state },
            onClick = { showHelp = !showHelp },
        ) {
            trailing()
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = helpLabel)
        }
        if (showHelp) {
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Lined up with the heading's text.
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun Band(
    text: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
) {
    val content = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
    val bandModifier = modifier
        .fillMaxWidth()
        .semantics { heading() }
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = MaterialTheme.shapes.small
    if (onClick == null) {
        Surface(modifier = bandModifier, shape = shape, color = color, content = content)
    } else {
        Surface(onClick = onClick, modifier = bandModifier, shape = shape, color = color, content = content)
    }
}
