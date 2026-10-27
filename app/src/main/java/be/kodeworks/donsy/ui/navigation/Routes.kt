// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.navigation

import kotlinx.serialization.Serializable

// Each tab is its own nested graph so it keeps its own back stack when switching tabs.

@Serializable data object HomeGraph
@Serializable data object HomeRoute

@Serializable data object BrainDumpGraph
@Serializable data object BrainDumpRoute

@Serializable data object TasksGraph
@Serializable data object TasksRoute
@Serializable data class TaskDetailRoute(val taskId: Long)

@Serializable data object FocusGraph
@Serializable data object FocusRoute

@Serializable data object SettingsRoute
@Serializable data object RecentlyDeletedRoute
@Serializable data object ArchiveRoute
@Serializable data object LicensesRoute

/** [license] is an `OpenSourceLicense` name: a plain string survives R8, unlike an enum argument. */
@Serializable data class LicenseTextRoute(val license: String)

/**
 * Full-screen task editor. With neither id it creates a new task; with [brainDumpItemId] it
 * turns that thought into a task.
 */
@Serializable
data class TaskEditRoute(val taskId: Long = NO_ID, val brainDumpItemId: Long = NO_ID) {
    companion object {
        const val NO_ID = -1L
    }
}

/** Keys for results handed back to the previous screen through its SavedStateHandle. */
object NavResults {
    /** Long: id of a task deleted on its detail screen, so the list can offer Undo. */
    const val TASK_DELETED = "task_deleted"

    /** Boolean: a BrainDump thought was turned into a task. */
    const val THOUGHT_CONVERTED = "thought_converted"
}
