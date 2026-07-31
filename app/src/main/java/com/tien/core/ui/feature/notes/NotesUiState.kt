package com.tien.core.ui.feature.notes

import androidx.compose.runtime.Immutable
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.NoteSort

/**
 * Everything the notes screen renders.
 *
 * `@Immutable` tells the Compose compiler it may skip recomposing children whose
 * state instance is unchanged. It is a promise the type has to keep — hence the
 * `List` here is only ever replaced, never mutated in place.
 *
 * Filtering and sorting are **not** computed here. They used to be derived
 * properties on the state object, which meant every recomposition re-filtered
 * and re-sorted the entire note list on the main thread. They are now part of
 * the SQL query.
 */
@Immutable
data class NotesUiState(
    val pinned: List<Note> = emptyList(),
    val others: List<Note> = emptyList(),
    val query: String = "",
    val sort: NoteSort = NoteSort.DEFAULT,
    val isLoading: Boolean = true,
    val failure: NotesFailure? = null
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()

    /** True when a search is active but matched nothing — a different empty state. */
    val isEmptySearch: Boolean get() = isEmpty && query.isNotBlank()

    val totalCount: Int get() = pinned.size + others.size
}

/** A load failure that replaces the list, as opposed to a transient message. */
@Immutable
data class NotesFailure(
    val title: String,
    val body: String
)

/** One-shot effects. Modelled separately so they fire once, not on every recomposition. */
sealed interface NotesEvent {
    data class ShowMessage(val text: String) : NotesEvent

    /** Deletion confirmed, with the undo affordance. */
    data class NoteDeleted(val noteId: Long) : NotesEvent
}
