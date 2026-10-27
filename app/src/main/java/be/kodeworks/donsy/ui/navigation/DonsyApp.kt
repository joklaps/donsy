// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import be.kodeworks.donsy.feature.braindump.BrainDumpScreen
import be.kodeworks.donsy.feature.focus.FocusScreen
import be.kodeworks.donsy.feature.home.HomeScreen
import be.kodeworks.donsy.feature.settings.SettingsScreen
import be.kodeworks.donsy.feature.settings.archive.ArchiveScreen
import be.kodeworks.donsy.feature.settings.deleted.RecentlyDeletedScreen
import be.kodeworks.donsy.feature.settings.licenses.LicenseTextScreen
import be.kodeworks.donsy.feature.settings.licenses.LicensesScreen
import be.kodeworks.donsy.feature.settings.licenses.OpenSourceLicense
import be.kodeworks.donsy.feature.tasks.TasksScreen
import be.kodeworks.donsy.feature.tasks.detail.TaskDetailScreen
import be.kodeworks.donsy.feature.tasks.edit.TaskEditResult
import be.kodeworks.donsy.feature.tasks.edit.TaskEditScreen
import be.kodeworks.donsy.ui.theme.LocalTabAccent
import be.kodeworks.donsy.ui.theme.TabAccent
import be.kodeworks.donsy.ui.theme.tabAccent

@Composable
fun DonsyApp(
    navController: NavHostController = rememberNavController(),
    /** Incremented when a focus notification is tapped: switch to the Focus tab. */
    openFocusRequests: Int = 0,
    /** Incremented when a routine reminder is tapped: switch to the Tasks tab. */
    openTasksRequests: Int = 0,
) {
    LaunchedEffect(openFocusRequests) {
        if (openFocusRequests > 0) navController.navigateToTabRoot(TopLevelDestination.FOCUS, FocusRoute)
    }
    LaunchedEffect(openTasksRequests) {
        if (openTasksRequests > 0) navController.navigateToTabRoot(TopLevelDestination.TASKS, TasksRoute)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { currentDestination.isIn(it) }
    val openSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } }

    Scaffold(
        // Screens draw their own top bars and handle the status-bar inset themselves.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Settings and other full-screen destinations hide the bottom bar.
            if (currentTab != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = destination == currentTab
                        NavigationBarItem(
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                // Each tab's own pastel, only on the selected pill.
                                indicatorColor = MaterialTheme.colorScheme.tabAccent(destination.tint),
                            ),
                            onClick = { navController.navigateToTab(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            // One line, so long translations don't push their icon out of line with the others.
                            label = { Text(stringResource(destination.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeGraph,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            navigation<HomeGraph>(startDestination = HomeRoute) {
                tabComposable<HomeRoute>(TopLevelDestination.HOME) { entry ->
                    val deletedTaskId by entry.savedStateHandle
                        .getStateFlow<Long?>(NavResults.TASK_DELETED, null)
                        .collectAsStateWithLifecycle()
                    HomeScreen(
                        onOpenSettings = openSettings,
                        onStartFocus = { navController.navigateToTabRoot(TopLevelDestination.FOCUS, FocusRoute) },
                        onChooseTask = { navController.navigateToTabRoot(TopLevelDestination.TASKS, TasksRoute) },
                        onSortBrainDump = { navController.navigateToTabRoot(TopLevelDestination.BRAIN_DUMP, BrainDumpRoute) },
                        onNewTask = { navController.navigate(TaskEditRoute()) },
                        deletedTaskId = deletedTaskId,
                        onDeletedTaskHandled = { entry.savedStateHandle[NavResults.TASK_DELETED] = null },
                    )
                }
            }
            navigation<BrainDumpGraph>(startDestination = BrainDumpRoute) {
                tabComposable<BrainDumpRoute>(TopLevelDestination.BRAIN_DUMP) { entry ->
                    val converted by entry.savedStateHandle
                        .getStateFlow(NavResults.THOUGHT_CONVERTED, false)
                        .collectAsStateWithLifecycle()
                    BrainDumpScreen(
                        onOpenSettings = openSettings,
                        onMakeTask = { navController.navigate(TaskEditRoute(brainDumpItemId = it)) },
                        thoughtConverted = converted,
                        onThoughtConvertedHandled = { entry.savedStateHandle[NavResults.THOUGHT_CONVERTED] = false },
                    )
                }
            }
            navigation<TasksGraph>(startDestination = TasksRoute) {
                tabComposable<TasksRoute>(TopLevelDestination.TASKS) { entry ->
                    val deletedTaskId by entry.savedStateHandle
                        .getStateFlow<Long?>(NavResults.TASK_DELETED, null)
                        .collectAsStateWithLifecycle()
                    TasksScreen(
                        onOpenSettings = openSettings,
                        onOpenTask = { navController.navigate(TaskDetailRoute(it)) },
                        onNewTask = { navController.navigate(TaskEditRoute()) },
                        deletedTaskId = deletedTaskId,
                        onDeletedTaskHandled = { entry.savedStateHandle[NavResults.TASK_DELETED] = null },
                    )
                }
                tabComposable<TaskDetailRoute>(TopLevelDestination.TASKS) {
                    TaskDetailScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(TaskEditRoute(taskId = it)) },
                        onDeleted = { id ->
                            navController.previousBackStackEntry?.savedStateHandle?.set(NavResults.TASK_DELETED, id)
                            navController.popBackStack()
                        },
                    )
                }
            }
            navigation<FocusGraph>(startDestination = FocusRoute) {
                tabComposable<FocusRoute>(TopLevelDestination.FOCUS) { FocusScreen(onOpenSettings = openSettings) }
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenRecentlyDeleted = { navController.navigate(RecentlyDeletedRoute) { launchSingleTop = true } },
                    onOpenArchive = { navController.navigate(ArchiveRoute) { launchSingleTop = true } },
                    onOpenLicenses = { navController.navigate(LicensesRoute) { launchSingleTop = true } },
                )
            }
            composable<RecentlyDeletedRoute> { RecentlyDeletedScreen(onNavigateBack = { navController.popBackStack() }) }
            composable<ArchiveRoute> { ArchiveScreen(onNavigateBack = { navController.popBackStack() }) }
            composable<LicensesRoute> {
                LicensesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenLicense = { navController.navigate(LicenseTextRoute(it.name)) { launchSingleTop = true } },
                )
            }
            composable<LicenseTextRoute> { entry ->
                LicenseTextScreen(
                    license = OpenSourceLicense.valueOf(entry.toRoute<LicenseTextRoute>().license),
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<TaskEditRoute> {
                TaskEditScreen(
                    onClose = { navController.popBackStack() },
                    onSaved = { result ->
                        when (result) {
                            // A new task opens straight away, ready to be broken into steps.
                            is TaskEditResult.Created -> navController.navigate(TaskDetailRoute(result.taskId)) {
                                popUpTo<TaskEditRoute> { inclusive = true }
                            }
                            is TaskEditResult.Converted -> {
                                navController.previousBackStackEntry?.savedStateHandle?.set(NavResults.THOUGHT_CONVERTED, true)
                                navController.popBackStack()
                            }
                            TaskEditResult.Updated -> navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}

/** A destination inside [tab]: its top bar shows the tab's coloured icon. Settings and the task editor have none. */
private inline fun <reified T : Any> NavGraphBuilder.tabComposable(
    tab: TopLevelDestination,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry ->
    CompositionLocalProvider(LocalTabAccent provides TabAccent(tab.selectedIcon, tab.tint)) { content(entry) }
}

private fun NavDestination?.isIn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.graphClass) } == true

/** Switches to [tab] and shows its first screen, even if something else was left open there. */
private fun NavHostController.navigateToTabRoot(tab: TopLevelDestination, root: Any) {
    navigateToTab(tab)
    popBackStack(root, inclusive = false)
}

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
