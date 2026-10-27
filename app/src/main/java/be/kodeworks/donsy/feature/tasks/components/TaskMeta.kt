// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SubdirectoryArrowLeft
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import be.kodeworks.donsy.R
import be.kodeworks.donsy.domain.model.Effort

/** Small icon + text label. Meaning is always carried by the text, never by color alone. */
@Composable
fun MetaLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

object TaskMetaIcons {
    val due = Icons.Outlined.CalendarToday
    val important = Icons.Outlined.Star
    val steps = Icons.Outlined.Checklist
    val estimate = Icons.Outlined.Schedule
    val effort = Icons.Outlined.Bolt
    val repeat = Icons.Outlined.Repeat
    val doneToday = Icons.Outlined.Done

    /** A whole task: the Tasks tab's icon (see [TaskMarker]). */
    val task = Icons.Filled.TaskAlt

    /** A step inside a task. The arrow has no auto-mirrored variant, so it is picked per layout direction. */
    val step: ImageVector
        @Composable get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            Icons.Outlined.SubdirectoryArrowLeft
        } else {
            Icons.Outlined.SubdirectoryArrowRight
        }
}

@Composable
fun stepsProgressText(done: Int, total: Int): String =
    pluralStringResource(R.plurals.steps_progress, total, done, total)

/** "5 min" … "1 hour or more" for the estimate choices. */
@Composable
fun estimateText(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.estimate_hour_plus) else pluralStringResource(R.plurals.estimate_minutes, minutes, minutes)

/** A plain number of minutes, e.g. a focus length; unlike [estimateText] never "1 hour or more". */
@Composable
fun minutesText(minutes: Int): String = pluralStringResource(R.plurals.estimate_minutes, minutes, minutes)

@Composable
fun effortText(effort: Effort): String = stringResource(
    when (effort) {
        Effort.LOW -> R.string.effort_low
        Effort.MEDIUM -> R.string.effort_medium
        Effort.HIGH -> R.string.effort_high
    },
)

/** The estimate choices offered in the editor, in minutes; 60 stands for "an hour or more". */
val EstimateChoices = listOf(5, 15, 30, 60)
