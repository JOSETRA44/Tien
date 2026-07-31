package com.tien.core.ui.feature.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.core.result.AppResult
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.core.time.TienClock
import com.tien.core.domain.model.Priority
import com.tien.core.domain.model.Task
import com.tien.core.domain.model.TaskFilter
import com.tien.core.domain.repository.PreferencesRepository
import com.tien.core.domain.repository.TaskRepository
import com.tien.core.ui.designsystem.theme.Urgency
import com.tien.core.ui.feature.notes.toMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Presentation logic for the agenda.
 *
 * Grouping and urgency classification happen here, once per emission, rather
 * than inside composables where they would re-run on every recomposition.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AgendaViewModel(
    private val taskRepository: TaskRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: TienClock,
    private val labels: DateTimeLabels
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TaskFilter.DEFAULT)
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val retryTrigger = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    private val _events = Channel<AgendaEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var lastDeleted: Task? = null

    init {
        preferencesRepository.preferences
            .map { it.taskFilter }
            .distinctUntilChanged()
            .onEach { stored -> filter.value = stored }
            .launchIn(viewModelScope)

        observeTasks()
    }

    private fun observeTasks() {
        combine(
            query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
            filter,
            retryTrigger
        ) { text, activeFilter, _ -> text to activeFilter }
            .flatMapLatest { (text, activeFilter) ->
                // The day filter is applied after the query rather than inside
                // it: the chip strip has to show every day that has tasks, and
                // narrowing the SQL to one day would erase the other chips.
                taskRepository.observeTasks(text, activeFilter, day = null)
            }
            .onEach { result -> render(result) }
            .launchIn(viewModelScope)

        // Re-render when only the day selection changes — no new query needed.
        selectedDay
            .onEach { _uiState.update { state -> state.copy(selectedDay = it) } }
            .launchIn(viewModelScope)
    }

    private fun render(result: AppResult<List<Task>>) {
        when (result) {
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    sections = emptyList(),
                    availableDays = emptyList(),
                    isLoading = false,
                    failure = AgendaFailure(
                        title = "No se pudo cargar la agenda",
                        body = "Vuelve a intentarlo."
                    )
                )
            }

            is AppResult.Success -> {
                val now = clock.nowEpochSeconds()
                val todayEnd = clock.dayRange(clock.today()).endExclusive
                val tasks = result.data

                val classified = tasks.map { task ->
                    AgendaTask(task, Urgency.of(task, now, todayEnd))
                }

                val byDay = classified.groupBy { clock.toLocalDate(it.task.dueAt) }

                val chips = byDay
                    .map { (date, dayTasks) ->
                        DayChip(
                            date = date,
                            label = labels.dayLabel(date),
                            taskCount = dayTasks.count { !it.task.isDone },
                            hasOverdue = dayTasks.any { it.urgency == Urgency.OVERDUE }
                        )
                    }
                    .sortedBy { it.date }

                val day = selectedDay.value
                val visible = if (day != null) byDay.filterKeys { it == day } else byDay

                val sections = visible
                    .map { (date, dayTasks) ->
                        AgendaSection(
                            date = date,
                            label = labels.dayLabel(date),
                            // Within a day: open work first, then by time, then
                            // by how urgent it is.
                            tasks = dayTasks.sortedWith(
                                compareBy<AgendaTask> { it.task.isDone }
                                    .thenBy { it.task.dueAt }
                                    .thenByDescending { it.task.priority.nativeValue }
                            )
                        )
                    }
                    .sortedBy { it.date }

                _uiState.update {
                    it.copy(
                        sections = sections,
                        availableDays = chips,
                        summary = AgendaSummary(
                            pending = classified.count { t -> !t.task.isDone },
                            overdue = classified.count { t -> t.urgency == Urgency.OVERDUE },
                            completedToday = classified.count { t ->
                                t.task.isDone && clock.toLocalDate(t.task.updatedAt) == clock.today()
                            }
                        ),
                        isLoading = false,
                        failure = null
                    )
                }
            }
        }
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    fun onQueryChange(text: String) {
        query.value = text
        _uiState.update { it.copy(query = text) }
    }

    fun onClearQuery() = onQueryChange("")

    fun onFilterChange(newFilter: TaskFilter) {
        filter.value = newFilter
        _uiState.update { it.copy(filter = newFilter) }
        viewModelScope.launch { preferencesRepository.setTaskFilter(newFilter) }
    }

    /** Tapping the selected day again clears the filter. */
    fun onDaySelected(date: LocalDate?) {
        selectedDay.value = if (selectedDay.value == date) null else date
        retryTrigger.update { it + 1 }
    }

    fun onCreateTask(title: String, details: String, dueAt: Long, priority: Priority) {
        viewModelScope.launch {
            val result = taskRepository.create(title, details, dueAt, priority)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onUpdateTask(
        id: Long,
        title: String,
        details: String,
        dueAt: Long,
        priority: Priority
    ) {
        viewModelScope.launch {
            val result = taskRepository.update(id, title, details, dueAt, priority)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onToggleDone(task: Task) {
        viewModelScope.launch {
            val result = taskRepository.setDone(task.id, !task.isDone)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onDeleteTask(id: Long) {
        viewModelScope.launch {
            when (val result = taskRepository.delete(id)) {
                is AppResult.Success -> {
                    lastDeleted = result.data
                    _events.send(AgendaEvent.TaskDeleted(id))
                }

                is AppResult.Failure -> emitMessage(result.error.toMessage())
            }
        }
    }

    fun onUndoDelete() {
        val task = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val result = taskRepository.restore(task)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onRetry() {
        _uiState.update { it.copy(isLoading = true, failure = null) }
        retryTrigger.update { it + 1 }
    }

    private suspend fun emitMessage(text: String) {
        _events.send(AgendaEvent.ShowMessage(text))
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 220L
    }
}
