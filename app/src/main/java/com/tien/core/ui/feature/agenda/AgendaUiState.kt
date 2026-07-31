package com.tien.core.ui.feature.agenda

import androidx.compose.runtime.Immutable
import com.tien.core.domain.model.Task
import com.tien.core.domain.model.TaskFilter
import com.tien.core.ui.designsystem.theme.Urgency
import java.time.LocalDate

/** A task paired with its pre-computed urgency, so the UI never re-derives it. */
@Immutable
data class AgendaTask(
    val task: Task,
    val urgency: Urgency
)

/** Tasks sharing a due date, rendered under one day header. */
@Immutable
data class AgendaSection(
    val date: LocalDate,
    val label: String,
    val tasks: List<AgendaTask>
)

/**
 * Headline counts for the agenda.
 *
 * Shown as a sentence rather than a chart: with two numbers a chart is
 * decoration, and "2 vencidas" is the thing the user actually needs to see on
 * opening the screen.
 */
@Immutable
data class AgendaSummary(
    val pending: Int = 0,
    val overdue: Int = 0,
    val completedToday: Int = 0
) {
    val hasWork: Boolean get() = pending > 0 || overdue > 0
}

@Immutable
data class AgendaUiState(
    val sections: List<AgendaSection> = emptyList(),
    val summary: AgendaSummary = AgendaSummary(),
    val availableDays: List<DayChip> = emptyList(),
    val selectedDay: LocalDate? = null,
    val filter: TaskFilter = TaskFilter.DEFAULT,
    val query: String = "",
    val isLoading: Boolean = true,
    val failure: AgendaFailure? = null
) {
    val isEmpty: Boolean get() = sections.isEmpty()
    val isFiltered: Boolean get() = query.isNotBlank() || selectedDay != null ||
        filter != TaskFilter.ALL
}

/** A selectable day in the filter strip. */
@Immutable
data class DayChip(
    val date: LocalDate,
    val label: String,
    val taskCount: Int,
    val hasOverdue: Boolean
)

@Immutable
data class AgendaFailure(
    val title: String,
    val body: String
)

sealed interface AgendaEvent {
    data class ShowMessage(val text: String) : AgendaEvent
    data class TaskDeleted(val taskId: Long) : AgendaEvent
}
