package com.tien.core.domain.repository

import com.tien.core.core.result.AppResult
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.NoteSort
import kotlinx.coroutines.flow.Flow

/**
 * Contract for note storage. Declared in `domain` and implemented in `data`, so
 * the domain and UI layers never learn that persistence happens to be SQLite
 * behind JNI — swapping in Room, a server, or a fake for tests touches only the
 * implementation.
 */
interface NoteRepository {

    /**
     * Stream of notes matching [query] under [sort].
     *
     * Re-emits whenever the underlying data changes, so callers never poll and
     * never reload manually after a write. Filtering and ordering are pushed
     * into SQL rather than applied to an in-memory copy of the whole table.
     */
    fun observeNotes(query: String, sort: NoteSort): Flow<AppResult<List<Note>>>

    /** Creates a note and returns it with its assigned id and timestamps. */
    suspend fun create(title: String, content: String): AppResult<Note>

    /** Updates title/content, refreshing `updatedAt`. */
    suspend fun update(id: Long, title: String, content: String): AppResult<Unit>

    suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Unit>

    /** Deletes a note, returning the deleted row so an undo can restore it. */
    suspend fun delete(id: Long): AppResult<Note>

    /**
     * Re-inserts a previously deleted note with its original id and timestamps.
     * A plain `create` would give it a new identity and jump it to the top of
     * the list, which is not what "undo" means.
     */
    suspend fun restore(note: Note): AppResult<Unit>

    suspend fun findById(id: Long): AppResult<Note>
}
