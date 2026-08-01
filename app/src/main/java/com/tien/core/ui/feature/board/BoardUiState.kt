package com.tien.core.ui.feature.board

import androidx.compose.runtime.Immutable
import com.tien.core.domain.model.BoardLink
import com.tien.core.domain.model.BoardNote

/**
 * What the board screen renders.
 *
 * Notes arrive already ordered back-to-front by the repository, so the renderer
 * draws straight down the list and never sorts.
 */
@Immutable
data class BoardUiState(
    val notes: List<BoardNote> = emptyList(),
    val links: List<BoardLink> = emptyList(),

    /** The paper whose toolbar is showing, if any. */
    val selectedNoteId: Long? = null,

    /**
     * Set while the user is tying a thread: the first paper has been chosen and
     * the next tap picks the second.
     */
    val linkSourceId: Long? = null,

    val isLoading: Boolean = true,
    val failure: BoardFailure? = null
) {
    val isEmpty: Boolean get() = notes.isEmpty()

    val selectedNote: BoardNote? get() = notes.firstOrNull { it.id == selectedNoteId }

    val isLinking: Boolean get() = linkSourceId != null

    /**
     * Lookup for the thread layer, which resolves each link's endpoints by id.
     *
     * A stored value, not a `get()`: the thread layer reads it inside a draw
     * scope, and a computed property would rebuild the whole map on every frame
     * of a pan. Built once per state emission instead.
     */
    val notesById: Map<Long, BoardNote> = notes.associateBy { it.id }

    /** True when the two papers already have a thread between them. */
    fun isLinked(a: Long, b: Long): Boolean = links.any {
        (it.fromNoteId == a && it.toNoteId == b) || (it.fromNoteId == b && it.toNoteId == a)
    }
}

@Immutable
data class BoardFailure(
    val title: String,
    val body: String
)

/** One-shot effects, so they fire once rather than on every recomposition. */
sealed interface BoardEvent {
    data class ShowMessage(val text: String) : BoardEvent

    /** A paper was taken down; offers to put it back. */
    data class NoteRemoved(val noteId: Long) : BoardEvent

    /** Opens the editor for a paper. */
    data class EditNote(val noteId: Long) : BoardEvent
}
