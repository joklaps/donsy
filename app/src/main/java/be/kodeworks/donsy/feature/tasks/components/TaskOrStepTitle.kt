// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.kodeworks.donsy.R
import be.kodeworks.donsy.ui.theme.ScreenTint
import be.kodeworks.donsy.ui.theme.tabAccent

/** Where a step sits in its task: step [number] of [count]. */
data class StepPosition(val number: Int, val count: Int)

/**
 * A task, or a step shown under its task, so the two are never confused. Each is told apart by
 * three cues that work together: position (the task sits above its step), an icon and a text
 * badge ("Task" / "Step 2 of 5"). Colour only reinforces the badge.
 */
@Composable
fun TaskOrStepTitle(
    title: String,
    /** The task's title when [title] is a step; null for a whole task. */
    taskTitle: String?,
    step: StepPosition?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    // Read as one item by screen readers: "Biology test, Make flashcards, Step 2 of 5".
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (taskTitle != null) ParentTaskLine(taskTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindIcon(step, Modifier.padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = titleStyle)
                KindBadge(step)
            }
        }
    }
}

/** The task a step belongs to, as a small line above the step. */
@Composable
fun ParentTaskLine(taskTitle: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskMarker(size = 22.dp)
        Text(taskTitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The icon in front of a task or step title: [TaskMarker] for a task, an indented arrow for a step. */
@Composable
fun KindIcon(step: StepPosition?, modifier: Modifier = Modifier) {
    if (step != null) {
        Icon(
            TaskMetaIcons.step,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(28.dp).padding(2.dp),
        )
    } else {
        TaskMarker(size = 28.dp, modifier = modifier)
    }
}

/** A task: a check icon on the Tasks tab's peach accent. Decorative. */
@Composable
fun TaskMarker(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.tabAccent(ScreenTint.TASKS), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TaskMetaIcons.task, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.64f))
    }
}

/**
 * "Task" or "Step 2 of 5". A task's badge uses the Tasks tab's peach; a step's a tonal theme colour.
 * Both follow light and dark mode.
 */
@Composable
fun KindBadge(step: StepPosition?, modifier: Modifier = Modifier) {
    val (container, content) = if (step != null) {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.tabAccent(ScreenTint.TASKS) to MaterialTheme.colorScheme.onSurface
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            if (step != null) {
                pluralStringResource(R.plurals.step_position, step.count, step.number, step.count)
            } else {
                stringResource(R.string.kind_task)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
