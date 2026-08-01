package com.tien.core.domain.repository

import com.tien.core.core.result.AppResult
import com.tien.core.domain.model.BoardLink
import com.tien.core.domain.model.BoardNote
import com.tien.core.domain.model.PaperColor
import kotlinx.coroutines.flow.Flow

/** Everything pinned to one board, delivered together so the two stay in sync. */
data class BoardContent(
    val notes: List<BoardNote> = emptyList(),
    val links: List<BoardLink> = emptyList()
)

/**
 * Contract for board storage.
 *
 * The write surface is deliberately fine-grained. Dragging a paper changes only
 * its position, and a coarse `update(note)` would rewrite the text, size and
 * colour on every drop — turning a gesture into a much larger write than it is.
 */
interface BoardRepository {

    /** Stream of the board's contents, re-emitted after any change. */
    fun observeBoard(boardId: Long): Flow<AppResult<BoardContent>>

    /**
     * Pins a new paper at ([x], [y]) with the given [rotation].
     *
     * The caller supplies the tilt rather than the data layer generating one,
     * because it is part of the gesture that created the note — see
     * `BoardViewModel.onPinNote`.
     */
    suspend fun pinNote(
        boardId: Long,
        text: String,
        x: Float,
        y: Float,
        rotation: Float,
        color: PaperColor
    ): AppResult<BoardNote>

    suspend fun updateText(id: Long, text: String): AppResult<Unit>

    /** Persists a drag. Called once on drop, never per frame. */
    suspend fun updateTransform(id: Long, x: Float, y: Float, rotation: Float): AppResult<Unit>

    suspend fun updateSize(id: Long, width: Float, height: Float): AppResult<Unit>

    suspend fun updateColor(id: Long, color: PaperColor): AppResult<Unit>

    /** Brings a paper to the top of the pile, as picking one up would. */
    suspend fun raise(id: Long): AppResult<Unit>

    /**
     * Removes a paper, returning it so an undo can pin it back unchanged.
     *
     * [boardId] is required because the returned snapshot has to be read before
     * the row is deleted, and reading it means querying a specific board.
     */
    suspend fun removeNote(boardId: Long, id: Long): AppResult<BoardNote>

    suspend fun restoreNote(note: BoardNote): AppResult<Unit>

    /** Ties two papers together. Tying an already-tied pair is a no-op. */
    suspend fun link(boardId: Long, fromNoteId: Long, toNoteId: Long): AppResult<Unit>

    suspend fun unlink(fromNoteId: Long, toNoteId: Long): AppResult<Unit>
}
