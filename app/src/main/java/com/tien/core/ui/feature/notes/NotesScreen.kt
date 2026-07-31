package com.tien.core.ui.feature.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.NoteSort
import com.tien.core.ui.designsystem.component.EmptyState
import com.tien.core.ui.designsystem.component.ErrorState
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.component.SectionEyebrow
import com.tien.core.ui.designsystem.theme.TienTheme
import kotlinx.coroutines.launch

/**
 * Notes list.
 *
 * All persistent editing state is held as savable primitives — a `Long?` id and
 * two `String` drafts. The previous screen kept a whole `Note` in
 * `rememberSaveable`, which throws as soon as a non-null value is written
 * because `Note` cannot be put in a `Bundle`: deleting a note crashed the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    labels: DateTimeLabels,
    snackbarHostState: SnackbarHostState,
    showEditor: Boolean,
    onEditorDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Savable primitives only.
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftContent by rememberSaveable { mutableStateOf("") }

    fun closeEditor() {
        editingNoteId = null
        draftTitle = ""
        draftContent = ""
        onEditorDismissed()
    }

    // One-shot events, collected once per lifecycle.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NotesEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.text)

                is NotesEvent.NoteDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Nota eliminada",
                        actionLabel = "Deshacer",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onUndoDelete()
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        NotesToolbar(
            count = uiState.totalCount,
            searchVisible = searchVisible,
            sortMenuOpen = sortMenuOpen,
            currentSort = uiState.sort,
            onToggleSearch = {
                searchVisible = !searchVisible
                if (!searchVisible) viewModel.onClearQuery()
            },
            onSortMenuOpenChange = { sortMenuOpen = it },
            onSortChange = viewModel::onSortChange
        )

        AnimatedVisibility(
            visible = searchVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Buscar en tus notas") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = viewModel::onClearQuery) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = TienTheme.spacing.gutter,
                        vertical = TienTheme.spacing.snug
                    )
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.failure != null -> ErrorState(
                    title = uiState.failure!!.title,
                    body = uiState.failure!!.body,
                    onRetry = viewModel::onRetry
                )

                uiState.isLoading -> LoadingState()

                uiState.isEmptySearch -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Sin coincidencias",
                    body = "No hay notas que contengan “${uiState.query}”. Prueba con otra palabra."
                )

                uiState.isEmpty -> EmptyState(
                    icon = Icons.Outlined.Description,
                    title = "Empieza a escribir",
                    body = "Guarda una idea antes de que se te olvide. Toca + para crear tu primera nota."
                )

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = TienTheme.spacing.gutter,
                        end = TienTheme.spacing.gutter,
                        top = TienTheme.spacing.base,
                        bottom = TienTheme.spacing.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.base),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.pinned.isNotEmpty()) {
                        item(key = "eyebrow_pinned") {
                            SectionEyebrow(
                                text = "Fijadas",
                                modifier = Modifier.padding(bottom = TienTheme.spacing.tight)
                            )
                        }
                        items(uiState.pinned, key = { "pinned_${it.id}" }) { note ->
                            NoteCard(
                                note = note,
                                labels = labels,
                                onEdit = { selected ->
                                    editingNoteId = selected.id
                                    draftTitle = selected.title
                                    draftContent = selected.content
                                },
                                onDelete = { viewModel.onDeleteNote(it.id) },
                                onTogglePin = viewModel::onTogglePinned,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (uiState.others.isNotEmpty()) {
                        if (uiState.pinned.isNotEmpty()) {
                            item(key = "eyebrow_all") {
                                SectionEyebrow(
                                    text = "Todas",
                                    modifier = Modifier.padding(
                                        top = TienTheme.spacing.snug,
                                        bottom = TienTheme.spacing.tight
                                    )
                                )
                            }
                        }
                        items(uiState.others, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                labels = labels,
                                onEdit = { selected ->
                                    editingNoteId = selected.id
                                    draftTitle = selected.title
                                    draftContent = selected.content
                                },
                                onDelete = { viewModel.onDeleteNote(it.id) },
                                onTogglePin = viewModel::onTogglePinned,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    val editorOpen = showEditor || editingNoteId != null
    if (editorOpen) {
        val isEditing = editingNoteId != null
        NoteEditorSheet(
            sheetState = sheetState,
            initialTitle = draftTitle,
            initialContent = draftContent,
            isEditing = isEditing,
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { closeEditor() }
            },
            onSave = { title, content ->
                val id = editingNoteId
                if (id != null) {
                    viewModel.onUpdateNote(id, title, content)
                } else {
                    viewModel.onCreateNote(title, content)
                    // A new note lands at the top; scroll there so the user
                    // sees it was saved.
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesToolbar(
    count: Int,
    searchVisible: Boolean,
    sortMenuOpen: Boolean,
    currentSort: NoteSort,
    onToggleSearch: () -> Unit,
    onSortMenuOpenChange: (Boolean) -> Unit,
    onSortChange: (NoteSort) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TienTheme.spacing.gutter,
                end = TienTheme.spacing.snug,
                top = TienTheme.spacing.snug,
                bottom = TienTheme.spacing.tight
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notas",
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
            )
            SectionEyebrow(
                text = when (count) {
                    0 -> "Ninguna guardada"
                    1 -> "1 nota"
                    else -> "$count notas"
                }
            )
        }

        Box {
            IconButton(onClick = { onSortMenuOpenChange(true) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = "Ordenar notas"
                )
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { onSortMenuOpenChange(false) }
            ) {
                NoteSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        leadingIcon = {
                            // A radio, not a checkmark: these are exclusive, and
                            // the control should say so before it is used.
                            RadioButton(
                                selected = option == currentSort,
                                onClick = null
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            onSortMenuOpenChange(false)
                        }
                    )
                }
            }
        }

        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (searchVisible) "Cerrar búsqueda" else "Buscar notas"
            )
        }
    }
}

private fun NoteSort.label(): String = when (this) {
    NoteSort.RECENTLY_UPDATED -> "Editadas primero"
    NoteSort.OLDEST_FIRST -> "Más antiguas primero"
    NoteSort.TITLE_ASC -> "Título (A–Z)"
}
