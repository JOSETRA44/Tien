package com.tien.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tien.core.model.Note
import com.tien.core.viewmodel.NotesSort
import com.tien.core.viewmodel.NotesViewModel
import com.tien.core.viewmodel.formatDayLabel
import kotlinx.coroutines.launch

private enum class SheetMode {
    CREATE,
    EDIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    vm: NotesViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    val noteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val taskSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showNoteSheet by rememberSaveable { mutableStateOf(false) }
    var showTaskSheet by rememberSaveable { mutableStateOf(false) }
    var sheetMode by rememberSaveable { mutableStateOf(SheetMode.CREATE) }
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftContent by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=Notas,1=Agenda
    var deletedNote by rememberSaveable { mutableStateOf<Note?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHost.showSnackbar(it)
            vm.dismissError()
        }
    }

    LaunchedEffect(deletedNote) {
        val note = deletedNote ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar("Nota eliminada", actionLabel = "Deshacer")
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            vm.insertNote(note.title, note.content)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Tien Productivity") },
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(onClick = { sortExpanded = true }) {
                                Icon(Icons.Outlined.Sort, contentDescription = "Ordenar")
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false }
                            ) {
                                DropdownMenuItem(text = { Text("Más recientes") }, onClick = {
                                    vm.setSort(NotesSort.NEWEST); sortExpanded = false
                                })
                                DropdownMenuItem(text = { Text("Más antiguas") }, onClick = {
                                    vm.setSort(NotesSort.OLDEST); sortExpanded = false
                                })
                                DropdownMenuItem(text = { Text("Título (A-Z)") }, onClick = {
                                    vm.setSort(NotesSort.TITLE); sortExpanded = false
                                })
                            }
                        }

                        IconButton(onClick = onThemeToggle) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Cambiar tema"
                            )
                        }
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Buscar"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                AnimatedVisibility(
                    visible = searchActive,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = uiState.searchQuery,
                                onQueryChange = vm::setSearchQuery,
                                onSearch = {},
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = { Text("Buscar en notas y tareas...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotBlank()) {
                                        IconButton(onClick = { vm.setSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                        }
                                    }
                                }
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        content = {}
                    )
                }

                if (selectedTab == 1 && uiState.taskDays.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { vm.setSelectedDay(null) },
                            label = { Text("Todos") },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (uiState.selectedDayKey == null) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        )
                        uiState.taskDays.take(3).forEach { day ->
                            AssistChip(
                                onClick = { vm.setSelectedDay(day) },
                                label = { Text(formatDayLabel(day)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (uiState.selectedDayKey == day) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        sheetMode = SheetMode.CREATE
                        editingNoteId = null
                        draftTitle = ""
                        draftContent = ""
                        showNoteSheet = true
                    } else {
                        showTaskSheet = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("Notas") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                    label = { Text("Agenda") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                if (selectedTab == 0) {
                    NotesList(
                        notes = uiState.filteredNotes,
                        onEdit = { selected ->
                            sheetMode = SheetMode.EDIT
                            editingNoteId = selected.id
                            draftTitle = selected.title
                            draftContent = selected.content
                            showNoteSheet = true
                        },
                        onDelete = { selected ->
                            vm.deleteNote(selected.id)
                            deletedNote = selected
                        },
                        listState = listState
                    )
                } else {
                    AgendaList(
                        uiState = uiState,
                        onToggleDone = { id, done -> vm.toggleTaskDone(id, done) },
                        onDeleteTask = { id -> vm.deleteTask(id) }
                    )
                }
            }
        }
    }

    if (showNoteSheet) {
        AddNoteSheet(
            sheetState = noteSheetState,
            onDismiss = {
                scope.launch { noteSheetState.hide() }.invokeOnCompletion { showNoteSheet = false }
            },
            initialTitle = draftTitle,
            initialContent = draftContent,
            isEditing = sheetMode == SheetMode.EDIT,
            onSave = { title, content ->
                if (sheetMode == SheetMode.EDIT && editingNoteId != null) {
                    vm.updateNote(editingNoteId!!, title, content)
                } else {
                    vm.insertNote(title, content)
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        )
    }

    if (showTaskSheet) {
        AddTaskSheet(
            sheetState = taskSheetState,
            onDismiss = {
                scope.launch { taskSheetState.hide() }.invokeOnCompletion { showTaskSheet = false }
            },
            onSave = { title, details, dueAt, priority ->
                vm.insertTask(title, details, dueAt, priority)
            }
        )
    }
}

@Composable
private fun NotesList(
    notes: List<Note>,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (notes.isEmpty()) {
        EmptyState("No hay notas todavía", "Crea una nota con el botón +")
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun AgendaList(
    uiState: com.tien.core.viewmodel.NotesUiState,
    onToggleDone: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit
) {
    if (uiState.agendaTasks.isEmpty()) {
        EmptyState("Sin tareas en agenda", "Programa tareas con fecha de plazo desde +")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(uiState.agendaTasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onToggleDone = { done -> onToggleDone(task.id, done) },
                onDelete = { onDeleteTask(task.id) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.TaskAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
