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
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.rememberModalBottomSheetState
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var sheetMode by rememberSaveable { mutableStateOf(SheetMode.CREATE) }
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftContent by rememberSaveable { mutableStateOf("") }
    var deleteTarget by rememberSaveable { mutableStateOf<Note?>(null) }

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHost.showSnackbar(msg)
            vm.dismissError()
        }
    }

    LaunchedEffect(deleteTarget) {
        val target = deleteTarget ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = "Nota eliminada",
            actionLabel = "Deshacer",
            withDismissAction = true
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            vm.insertNote(target.title, target.content)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Tien",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Más recientes") },
                                onClick = {
                                    vm.setSort(NotesSort.NEWEST)
                                    sortExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Más antiguas") },
                                onClick = {
                                    vm.setSort(NotesSort.OLDEST)
                                    sortExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Título (A-Z)") },
                                onClick = {
                                    vm.setSort(NotesSort.TITLE)
                                    sortExpanded = false
                                }
                            )
                        }

                        IconButton(onClick = { onThemeToggle() }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Cambiar tema"
                            )
                        }

                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searchActive) "Cerrar búsqueda" else "Buscar"
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
                                placeholder = { Text("Buscar notas…") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
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

                if (uiState.availableDayKeys.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { vm.setSelectedDay(null) },
                            label = { Text("Todo") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ClearAll,
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (uiState.selectedDayKey == null) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        )
                        uiState.availableDayKeys.take(3).forEach { day ->
                            FilterChip(
                                selected = uiState.selectedDayKey == day,
                                onClick = { vm.setSelectedDay(day) },
                                label = { Text(formatDayLabel(day)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    sheetMode = SheetMode.CREATE
                    editingNoteId = null
                    draftTitle = ""
                    draftContent = ""
                    showSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nueva nota")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.filtered.isEmpty() -> {
                    EmptyState(
                        isSearching = uiState.searchQuery.isNotBlank() || uiState.selectedDayKey != null,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        uiState.groupedByDay.forEach { (dayKey, notes) ->
                            item(key = "header-$dayKey") {
                                Text(
                                    text = formatDayLabel(dayKey),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            items(items = notes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    onEdit = { selected ->
                                        sheetMode = SheetMode.EDIT
                                        editingNoteId = selected.id
                                        draftTitle = selected.title
                                        draftContent = selected.content
                                        showSheet = true
                                    },
                                    onDelete = { selected ->
                                        vm.deleteNote(selected.id)
                                        deleteTarget = selected
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        AddNoteSheet(
            sheetState = sheetState,
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
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
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearching) "Sin resultados" else "Aún no hay notas",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSearching) "Prueba con otros filtros o palabras."
            else "Pulsa + para crear tu primera nota.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
