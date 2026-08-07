package com.tien.core.ui.feature.dutic

import androidx.compose.runtime.Immutable
import com.tien.dutic.domain.model.CourseGrades
import com.tien.dutic.domain.model.DuticCourse
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.repository.AssignmentDetail

/** Which slice of the workload the list is showing. */
enum class DuticFilter {
    /** Everything still to hand in. The default: it is the question being asked. */
    PENDING,

    /** Only what the calendar would never have shown. */
    HIDDEN,

    ALL;

    val label: String
        get() = when (this) {
            PENDING -> "Sin entregar"
            HIDDEN -> "Ocultas"
            ALL -> "Todas"
        }
}

/**
 * The comparison the whole feature exists to make.
 *
 * Moodle's calendar returns only *actionable* events — future and unsubmitted —
 * so anything overdue or undated silently disappears. Putting [visibleInCalendar]
 * next to [pending] turns that omission into a number the student can see, which
 * is the single most useful thing this screen does.
 */
@Immutable
data class DuticSummary(
    /** Everything still to hand in, from the full sweep. */
    val pending: Int = 0,

    /** How many of those the calendar actually shows. */
    val visibleInCalendar: Int = 0,

    val overdue: Int = 0,
    val courses: Int = 0
) {
    /** What the calendar omits. The headline. */
    val hidden: Int get() = (pending - visibleInCalendar).coerceAtLeast(0)

    val hasHidden: Boolean get() = hidden > 0

    val isClear: Boolean get() = pending == 0
}

@Immutable
data class DuticUiState(
    val isSignedIn: Boolean = false,
    val semester: String? = null,
    val displayName: String? = null,

    val tasks: List<DuticTask> = emptyList(),
    val courses: List<DuticCourse> = emptyList(),
    val grades: List<CourseGrades> = emptyList(),
    val summary: DuticSummary = DuticSummary(),

    /** Which of the three home sections is showing. */
    val homeTab: DuticHomeTab = DuticHomeTab.TASKS,
    val filter: DuticFilter = DuticFilter.PENDING,

    /** Grades are fetched only when their tab is first opened. */
    val isLoadingGrades: Boolean = false,

    /** The assignment whose brief is open, if any. */
    val openTaskId: Long? = null,
    val openTaskDetail: AssignmentDetail? = null,
    val isLoadingDetail: Boolean = false,

    /** True during the first load, when there is nothing to show yet. */
    val isLoading: Boolean = false,

    /**
     * True while the slow, accurate sweep runs behind an already-painted list.
     *
     * Separate from [isLoading] on purpose: replacing a visible list with a
     * spinner every time it refreshes is worse than a thin progress line over
     * the data the user is already reading.
     */
    val isRefreshing: Boolean = false,

    val failure: DuticFailure? = null
) {
    /** The list as filtered, already ordered by urgency upstream. */
    val visibleTasks: List<DuticTask>
        get() = when (filter) {
            DuticFilter.PENDING -> tasks.filter { it.isPending }
            DuticFilter.HIDDEN -> tasks.filter { it.hidden }
            DuticFilter.ALL -> tasks
        }

    val isEmpty: Boolean get() = visibleTasks.isEmpty()

    /**
     * Courses that have at least one marked item, for the grades section.
     * A course with nothing graded yet is noise on a screen about results.
     */
    val gradedCourses: List<CourseGrades> get() = grades.filter { it.gradedCount > 0 }

    val openTask: DuticTask? get() = tasks.firstOrNull { it.id == openTaskId }
}

@Immutable
data class DuticFailure(
    val title: String,
    val body: String,
    /** True when signing in again is what fixes it. */
    val needsSignIn: Boolean = false
)

sealed interface DuticEvent {
    data class ShowMessage(val text: String) : DuticEvent

    /** The session died; the UI should offer the login screen. */
    data object RequireSignIn : DuticEvent
}
