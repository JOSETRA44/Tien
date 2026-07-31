package com.tien.core.data.repository

import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.data.mapper.NativePayloadMapper
import com.tien.core.data.nativedb.NativeConnection
import com.tien.core.data.nativedb.NativeDatabase
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.NoteSort
import com.tien.core.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * SQLite-backed [NoteRepository], talking to the native layer through
 * [NativeConnection].
 *
 * **Invalidation model.** Writes bump an in-memory revision counter; every
 * active query stream is derived from that counter, so a mutation anywhere
 * refreshes every observer exactly once. The previous design had the ViewModel
 * call `loadAll()` by hand after each write, which re-read *both* tables even
 * when a single checkbox changed — and quietly did nothing if a caller forgot.
 */
internal class NoteRepositoryImpl(
    private val connection: NativeConnection,
    private val dispatcher: CoroutineDispatcher
) : NoteRepository {

    private val revision = MutableStateFlow(0L)

    override fun observeNotes(query: String, sort: NoteSort): Flow<AppResult<List<Note>>> =
        revision
            .map { fetchNotes(query, sort) }
            .flowOn(dispatcher)

    private fun fetchNotes(query: String, sort: NoteSort): AppResult<List<Note>> {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return unavailable()

        val payload = NativeDatabase.nativeQueryNotes(
            handle = handle,
            query = query.trim().toByteArray(Charsets.UTF_8),
            sort = sort.nativeValue
        )
        return NativePayloadMapper.decodeList(payload, NativePayloadMapper::toNote)
    }

    override suspend fun create(title: String, content: String): AppResult<Note> =
        withContext(dispatcher) {
            val cleanTitle = title.trim()
            if (cleanTitle.isBlank()) {
                return@withContext AppResult.Failure(
                    DataError.Validation(DataError.Validation.Field.TITLE)
                )
            }

            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeInsertNote(
                handle = handle,
                title = cleanTitle.toByteArray(Charsets.UTF_8),
                content = content.trim().toByteArray(Charsets.UTF_8)
            )

            when (val inserted = connection.toResult(code)) {
                is AppResult.Failure -> inserted
                is AppResult.Success -> {
                    invalidate()
                    // Re-read so the caller gets the timestamps SQLite assigned
                    // rather than a client-side guess at "now".
                    findByIdInternal(inserted.data)
                }
            }
        }

    override suspend fun update(id: Long, title: String, content: String): AppResult<Unit> =
        withContext(dispatcher) {
            val cleanTitle = title.trim()
            if (cleanTitle.isBlank()) {
                return@withContext AppResult.Failure(
                    DataError.Validation(DataError.Validation.Field.TITLE)
                )
            }

            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeUpdateNote(
                handle = handle,
                id = id,
                title = cleanTitle.toByteArray(Charsets.UTF_8),
                content = content.trim().toByteArray(Charsets.UTF_8)
            )
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Unit> =
        withContext(dispatcher) {
            val handle = connection.handle()
            if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

            val code = NativeDatabase.nativeSetNotePinned(handle, id, pinned)
            connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
        }

    override suspend fun delete(id: Long): AppResult<Note> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        // Read before deleting: once the row is gone there is nothing left to
        // hand back for the undo action.
        val snapshot = findByIdInternal(id)
        if (snapshot is AppResult.Failure) return@withContext snapshot

        val code = NativeDatabase.nativeDeleteNote(handle, id)
        when (val deleted = connection.toUnitResult(code)) {
            is AppResult.Failure -> deleted
            is AppResult.Success -> {
                invalidate()
                snapshot
            }
        }
    }

    override suspend fun restore(note: Note): AppResult<Unit> = withContext(dispatcher) {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return@withContext unavailable()

        val code = NativeDatabase.nativeRestoreNote(
            handle = handle,
            id = note.id,
            title = note.title.toByteArray(Charsets.UTF_8),
            content = note.content.toByteArray(Charsets.UTF_8),
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            pinned = note.pinned
        )
        connection.toUnitResult(code).also { if (it.isSuccess) invalidate() }
    }

    override suspend fun findById(id: Long): AppResult<Note> = withContext(dispatcher) {
        findByIdInternal(id)
    }

    /** Indexed single-row lookup — never reads the whole table. */
    private fun findByIdInternal(id: Long): AppResult<Note> {
        val handle = connection.handle()
        if (handle == NativeConnection.NO_HANDLE) return unavailable()

        val payload = NativeDatabase.nativeFindNote(handle, id)
        return when (val rows = NativePayloadMapper.decodeList(payload, NativePayloadMapper::toNote)) {
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
