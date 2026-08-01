package com.tien.core.ui.feature.agenda

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.core.time.TienClock
import com.tien.core.domain.model.Priority
import com.tien.core.domain.model.TaskFilter
import com.tien.core.ui.designsystem.component.EmptyState
import com.tien.core.ui.designsystem.component.ErrorState
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.component.SectionEyebrow
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme
import kotlinx.coroutines.launch

/**
 * Agenda.
 *
 * Opens with a one-line status ("4 pendientes · 1 vencida") because that is the
 * question the screen exists to answer. Everything below it is the detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    clock: TienClock,
    labels: DateTimeLabels,
    snackbarHostState: SnackbarHostState,
    showEditor: Boolean,
    onEditorDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchVisible by rememberSaveable { mutableStateOf(false) }

    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draftTitle by rememberSaveable { mutableStateOf("") }
    var draftDetails by rememberSaveable { mutableStateOf("") }
    var draftDueAt by rememberSaveable { mutableLongStateOf(0L) }
    var draftPriority by rememberSaveable { mutableIntStateOf(Priority.DEFAULT.ordinal) }

    fun closeEditor() {
        editingTaskId = null
        draftTitle = ""
        draftDetails = ""
        draftDueAt = 0L
        draftPriority = Priority.DEFAULT.ordinal
        onEditorDismissed()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AgendaEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.text)

                is AgendaEvent.TaskDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Tarea eliminada",
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
        AgendaHeader(
            summary = uiState.summary,
            searchVisible = searchVisible,
            onToggleSearch = {
                searchVisible = !searchVisible
                if (!searchVisible) viewModel.onClearQuery()
            }
        )

        AnimatedVisibility(
            visible = searchVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Buscar en tus tareas") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = viewModel::onClearQuery) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = TienTheme.spacing.gutter,
                        vertical = TienTheme.spacing.snug
                    )
            )
        }

        FilterRow(
            filter = uiState.filter,
            onFilterChange = viewModel::onFilterChange
        )

        if (uiState.availableDays.isNotEmpty()) {
            DayStrip(
                days = uiState.availableDays,
                selected = uiState.selectedDay,
                onSelect = viewModel::onDaySelected
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

                uiState.isEmpty && uiState.isFiltered -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Nada que mostrar aquí",
                    body = "Ningún plazo coincide con estos filtros. Quítalos para ver toda tu agenda."
                )

                uiState.isEmpty -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = "Tu agenda está libre",
                    body = "Programa lo próximo que tengas que entregar. Toca + para añadir una tarea."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = TienTheme.spacing.gutter,
                        end = TienTheme.spacing.gutter,
                        top = TienTheme.spacing.base,
                        bottom = TienTheme.spacing.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.base),
                    modifier = Modifier.fillMaxSize()
                ) {
                    uiState.sections.forEach { section ->
                        item(key = "header_${section.date}") {
                            SectionEyebrow(
                                text = section.label,
                                modifier = Modifier.padding(
                                    top = TienTheme.spacing.tight,
                                    bottom = TienTheme.spacing.tight
                                )
                            )
                        }
                        items(
                            items = section.tasks,
                            key = { it.task.id }
                        ) { item ->
                            TaskCard(
                                item = item,
                                labels = labels,
                                onToggleDone = { viewModel.onToggleDone(item.task) },
                                onEdit = {
                                    editingTaskId = item.task.id
                                    draftTitle = item.task.title
                                    draftDetails = item.task.details
                                    draftDueAt = item.task.dueAt
                                    draftPriority = item.task.priority.ordinal
                                },
                                onDelete = { viewModel.onDeleteTask(item.task.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    val editorOpen = showEditor || editingTaskId != null
    if (editorOpen) {
        TaskEditorSheet(
            sheetState = sheetState,
            clock = clock,
            labels = labels,
            initialTitle = draftTitle,
            initialDetails = draftDetails,
            initialDueAt = draftDueAt,
            initialPriority = Priority.entries[draftPriority],
            isEditing = editingTaskId != null,
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { closeEditor() }
            },
            onSave = { title, details, dueAt, priority ->
                val id = editingTaskId
                if (id != null) {
                    viewModel.onUpdateTask(id, title, details, dueAt, priority)
                } else {
                    viewModel.onCreateTask(title, details, dueAt, priority)
                }
            }
        )
    }
}

@Composable
private fun AgendaHeader(
    summary: AgendaSummary,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit
) {
    Row(
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
                text = "Agenda",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            // The status line is the point of the screen, so it is a sentence
            // rather than a set of counters.
            AgendaStatusLine(summary)
        }

        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (searchVisible) "Cerrar búsqueda" else "Buscar tareas"
            )
        }
    }
}

@Composable
private fun AgendaStatusLine(summary: AgendaSummary) {
    val extended = TienTheme.extendedColors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
    ) {
        if (!summary.hasWork) {
            SectionEyebrow(text = "Todo al día")
            return@Row
        }

        SectionEyebrow(
            text = when (summary.pending) {
                1 -> "1 pendiente"
                else -> "${summary.pending} pendientes"
            }
        )

        if (summary.overdue > 0) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(extended.muted)
            )
            SectionEyebrow(
                text = when (summary.overdue) {
                    1 -> "1 vencida"
                    else -> "${summary.overdue} vencidas"
                },
                color = extended.overdue
            )
        }
    }
}

@Composable
private fun FilterRow(
    filter: TaskFilter,
    onFilterChange: (TaskFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = TienTheme.spacing.gutter,
                vertical = TienTheme.spacing.snug
            ),
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
    ) {
        TaskFilter.entries.forEach { option ->
            FilterChip(
                selected = option == filter,
                onClick = { onFilterChange(option) },
                label = { Text(option.label()) }
            )
        }
    }
}

// `List` is declared stable for this module in compose_compiler_config.conf:
// every list reaching a composable comes from an @Immutable UI state the
// ViewModel replaces wholesale rather than mutates. The compiler agrees — the
// generated report shows this composable as skippable — so the lint rule is
// the stricter of the two here.
@Suppress("ComposeUnstableCollections")
@Composable
private fun DayStrip(
    days: List<DayChip>,
    selected: java.time.LocalDate?,
    onSelect: (java.time.LocalDate?) -> Unit
) {
    val extended = TienTheme.extendedColors

    LazyRow(
        contentPadding = PaddingValues(horizontal = TienTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug),
        modifier = Modifier.padding(bottom = TienTheme.spacing.snug)
    ) {
        items(days, key = { it.date.toString() }) { day ->
            FilterChip(
                selected = day.date == selected,
                onClick = { onSelect(day.date) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(day.label)
                        if (day.taskCount > 0) {
                            Text(
                                text = day.taskCount.toString(),
                                style = TienTextStyles.eyebrow,
                                color = if (day.hasOverdue) {
                                    extended.overdue
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun TaskFilter.label(): String = when (this) {
    TaskFilter.ALL -> "Todas"
    TaskFilter.PENDING -> "Pendientes"
    TaskFilter.COMPLETED -> "Completadas"
}
