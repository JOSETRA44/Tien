package com.tien.core.data.repository

import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.data.mapper.NativePayloadMapper
import com.tien.core.data.nativedb.NativeConnection
import com.tien.core.data.nativedb.NativeDatabase
import com.tien.core.domain.model.Priority
import com.tien.core.domain.model.Task
import com.tien.core.domain.model.TaskFilter
import com.tien.core.domain.repository.DayRange
import com.tien.core.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * SQLite-backed [TaskRepository]. See [NoteRepositoryImpl] for the shared
 * invalidation model.
 */
internal class TaskRepositoryImpl(
    private val connection: NativeConnection,
    private val dispatcher: CoroutineDispatcher
) : TaskRepository {

    private val revision = MutableStateFlow(0L)

    override fun observeTasks(
        query: String,
        filter: TaskFilter,
        day: DayRange?
    ): Flow<AppResult<List<Task>>> =
        revision
            .map { fetchTasks(query, filter, day) }
            .flowOn(dispatcher)

    private fun fetchTasks(
        query: String,
        filter: TaskFilter,
        day: DayRange?
    ): AppResult<List<Task>> {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return unavailable()

        // A malformed range is dropped rather than passed down, where it would
        // silently match nothing.
        val range = day?.takeIf { it.isValid }

        val payload = NativeDatabase.nativeQueryTasks(
            handle = handle,
            query = query.trim().toByteArray(Charsets.UTF_8),
            filter = filter.nativeValue,
            dayStart = range?.startInclusive ?: 0L,
            dayEnd = range?.endExclusive ?: 0L
        )
        return NativePayloadMapper.decodeList(payload, NativePayloadMapper::toTask)
    }

    override suspend fun create(
        title: String,
        details: String,
        dueAt: Long,
        priority: Priority
    ): AppResult<Task> = withContext(dispatcher) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            return@withContext AppResult.Failure(
                DataError.Validation(DataError.Validation.Field.TITLE)
            )
        }
        if (dueAt <= 0L) {
            return@withContext AppResult.Failure(
                DataError.Validation(DataError.Validation.Field.DUE_DATE)
            )
        }

        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeInsertTask(
            handle = handle,
            title = cleanTitle.toByteArray(Charsets.UTF_8),
            details = details.trim().toByteArray(Charsets.UTF_8),
            dueAt = dueAt,
            priority = priority.nativeValue
        )

        when (val inserted = connection.toResult(code)) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> {
                invalidate()
                findByIdInternal(inserted.data)
            }
        }
    }

    override suspend fun update(
        id: Long,
        title: String,
        details: String,
        dueAt: Long,
        priority: Priority
    ): AppResult<Unit> = withContext(dispatcher) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            return@withContext AppResult.Failure(
                DataError.Validation(DataError.Validation.Field.TITLE)
            )
        }
        if (dueAt <= 0L) {
            return@withContext AppResult.Failure(
                DataError.Validation(DataError.Validation.Field.DUE_DATE)
            )
        }

        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeUpdateTask(
            handle = handle,
            id = id,
            title = cleanTitle.toByteArray(Charsets.UTF_8),
            details = details.trim().toByteArray(Charsets.UTF_8),
            dueAt = dueAt,
            priority = priority.nativeValue
        )
        connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Unit> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeSetTaskDone(handle, id, done)
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    override suspend fun delete(id: Long): AppResult<Task> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        // Snapshot first — after the DELETE there is nothing to hand to undo.
        val snapshot = findByIdInternal(id)
        if (snapshot is AppResult.Failure) return@withContext snapshot

        val code = NativeDatabase.nativeDeleteTask(handle, id)
        when (val deleted = connection.toUnitResult(code)) {
            is AppResult.Failure -> deleted
            is AppResult.Success -> {
                invalidate()
                snapshot
            }
        }
    }

    override suspend fun restore(task: Task): AppResult<Unit> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeRestoreTask(
            handle = handle,
            id = task.id,
            title = task.title.toByteArray(Charsets.UTF_8),
            details = task.details.toByteArray(Charsets.UTF_8),
            dueAt = task.dueAt,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            priority = task.priority.nativeValue,
            isDone = task.isDone
        )
        connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
    }

    override suspend fun findById(id: Long): AppResult<Task> = withContext(dispatcher) {
        findByIdInternal(id)
    }

    private fun findByIdInternal(id: Long): AppResult<Task> {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return unavailable()

        val payload = NativeDatabase.nativeFindTask(handle, id)
        return when (val rows = NativePayloadMapper.decodeList(payload, NativePayloadMapper::toTask)) {
            is AppResult.Failure -> rows
            is AppResult.Success ->
                rows.data.firstOrNull()
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(DataError.NotFound)
        }
    }

    private fun invalidate() {
        revision.update { it + 1 }
    }
}
