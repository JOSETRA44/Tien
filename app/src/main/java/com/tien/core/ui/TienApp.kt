package com.tien.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tien.core.di.AppContainer
import com.tien.core.ui.feature.agenda.AgendaScreen
import com.tien.core.ui.feature.agenda.AgendaViewModel
import com.tien.core.ui.feature.notes.NotesScreen
import com.tien.core.ui.feature.notes.NotesViewModel
import com.tien.core.ui.feature.settings.SettingsSheet
import com.tien.core.ui.navigation.TienDestination
import kotlinx.coroutines.launch

/**
 * Root scaffold: navigation bar, FAB, snackbar host and the two feature screens.
 *
 * The FAB and the snackbar live here rather than inside each screen so there is
 * exactly one of each. Both screens push into the same [SnackbarHostState],
 * which is what stops an undo prompt from a deleted note being replaced by an
 * unrelated message from the agenda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TienApp(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = TienDestination.fromRoute(currentRoute)

    // Which screen's editor the FAB should open. Held here because the FAB is
    // shared; each screen resets it when its sheet closes.
    var pendingCreateRoute by rememberSaveable { mutableStateOf<String?>(null) }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { pendingCreateRoute = currentDestination.route },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    // The label states the outcome and changes with the tab, so
                    // the control is never ambiguous about what it creates.
                    text = { Text(currentDestination.createLabel) }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                TienDestination.bottomBarItems.forEach { destination ->
                    val selected = backStackEntry?.destination?.hierarchy
                        ?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Keeps a single entry per tab instead of piling
                                // up a back stack of tab switches.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) {
                                    destination.selectedIcon
                                } else {
                                    destination.unselectedIcon
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }

                NavigationBarItem(
                    selected = false,
                    onClick = { showSettings = true },
                    icon = {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                    },
                    label = { Text("Ajustes") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = TienDestination.startRoute
            ) {
                composable(TienDestination.Notes.route) {
                    val viewModel: NotesViewModel = viewModel(
                        factory = TienViewModelFactory.notes(container)
                    )
                    NotesScreen(
                        viewModel = viewModel,
                        labels = container.dateTimeLabels,
                        snackbarHostState = snackbarHostState,
                        showEditor = pendingCreateRoute == TienDestination.Notes.route,
                        onEditorDismissed = { pendingCreateRoute = null }
                    )
                }

                composable(TienDestination.Agenda.route) {
                    val viewModel: AgendaViewModel = viewModel(
                        factory = TienViewModelFactory.agenda(container)
                    )
                    AgendaScreen(
                        viewModel = viewModel,
                        clock = container.clock,
                        labels = container.dateTimeLabels,
                        snackbarHostState = snackbarHostState,
                        showEditor = pendingCreateRoute == TienDestination.Agenda.route,
                        onEditorDismissed = { pendingCreateRoute = null }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            sheetState = settingsSheetState,
            themeMode = preferences.themeMode,
            dynamicColor = preferences.useDynamicColor,
            onThemeModeChange = settingsViewModel::onThemeModeChange,
            onDynamicColorChange = settingsViewModel::onDynamicColorChange,
            onDismiss = {
                scope.launch { settingsSheetState.hide() }
                    .invokeOnCompletion { showSettings = false }
            }
        )
    }
}
