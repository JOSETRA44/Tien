package com.tien.core.domain.repository

import com.tien.core.core.result.AppResult
import com.tien.core.domain.model.Priority
import com.tien.core.domain.model.Task
import com.tien.core.domain.model.TaskFilter
import kotlinx.coroutines.flow.Flow

/** Half-open range of epoch seconds `[startInclusive, endExclusive)`. */
data class DayRange(
    val startInclusive: Long,
    val endExclusive: Long
) {
    val isValid: Boolean get() = startInclusive > 0 && endExclusive > startInclusive
}

/**
 * Contract for task storage. See [NoteRepository] for the layering rationale.
 */
interface TaskRepository {

    /**
     * Stream of tasks matching [query] and [filter], optionally restricted to a
     * single day via [day]. All three narrow the SQL query rather than the
     * result list.
     */
    fun observeTasks(
        query: String,
        filter: TaskFilter,
        day: DayRange?
    ): Flow<AppResult<List<Task>>>

    suspend fun create(
        title: String,
        details: String,
        dueAt: Long,
        priority: Priority
    ): AppResult<Task>

    suspend fun update(
        id: Long,
        title: String,
        details: String,
        dueAt: Long,
        priority: Priority
    ): AppResult<Unit>

    suspend fun setDone(id: Long, done: Boolean): AppResult<Unit>

    /** Deletes a task, returning the deleted row so an undo can restore it. */
    suspend fun delete(id: Long): AppResult<Task>

    suspend fun restore(task: Task): AppResult<Unit>

    suspend fun findById(id: Long): AppResult<Task>
}
