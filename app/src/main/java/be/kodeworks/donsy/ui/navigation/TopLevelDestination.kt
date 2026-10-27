// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import be.kodeworks.donsy.R
import be.kodeworks.donsy.ui.theme.ScreenTint
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val graph: Any,
    val graphClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** The tab's pastel hue; its screens and the bottom bar's indicator use it. */
    val tint: ScreenTint,
) {
    HOME(HomeGraph, HomeGraph::class, R.string.tab_home, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt, ScreenTint.HOME),
    BRAIN_DUMP(
        BrainDumpGraph,
        BrainDumpGraph::class,
        R.string.tab_braindump,
        Icons.Filled.Lightbulb,
        Icons.Outlined.Lightbulb,
        ScreenTint.BRAIN_DUMP,
    ),
    TASKS(TasksGraph, TasksGraph::class, R.string.tab_tasks, Icons.Filled.Checklist, Icons.Outlined.Checklist, ScreenTint.TASKS),
    FOCUS(FocusGraph, FocusGraph::class, R.string.tab_focus, Icons.Filled.Timer, Icons.Outlined.Timer, ScreenTint.FOCUS),
}
