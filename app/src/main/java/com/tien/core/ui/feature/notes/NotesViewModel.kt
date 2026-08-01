package com.tien.core.ui.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.NoteSort
import com.tien.core.domain.repository.NoteRepository
import com.tien.core.domain.repository.PreferencesRepository
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

/**
 * Presentation logic for the notes screen.
 *
 * Notably *not* an `AndroidViewModel` holding a database path and calling JNI
 * directly, as before. Dependencies arrive through the constructor as
 * interfaces, so this class is testable with fakes and knows nothing about
 * SQLite, JNI, or Android.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesViewModel(
    private val noteRepository: NoteRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(NoteSort.DEFAULT)

    /** Bumped by [onRetry] to restart the query stream after a failure. */
    private val retryTrigger = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private val _events = Channel<NotesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Kept so the undo action can put back the exact row that was removed. */
    private var lastDeleted: Note? = null

    init {
        // Restore the persisted sort before the first query runs.
        preferencesRepository.preferences
            .map { it.noteSort }
            .distinctUntilChanged()
            .onEach { stored -> sort.value = stored }
            .launchIn(viewModelScope)

        observeNotes()
    }

    /** Started exactly once, from [init]. Retries re-trigger it from within. */
    private fun observeNotes() {
        combine(
            // Debounced so typing a 12-character search does not fire 12
            // queries; the trailing value is the one that matters.
            query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
            sort,
            retryTrigger
        ) { text, order, _ -> text to order }
            .flatMapLatest { (text, order) ->
                // flatMapLatest, not flatMapMerge: when the query changes, the
                // in-flight result for the previous one is stale and cancelling
                // it prevents an out-of-order overwrite.
                noteRepository.observeNotes(text, order)
            }
            .onEach { result -> render(result) }
            .launchIn(viewModelScope)
    }

    private fun render(result: AppResult<List<Note>>) {
        _uiState.update { current ->
            when (result) {
                is AppResult.Success -> current.copy(
                    // Pinned notes are split out here rather than in SQL: the
                    // query already returns them first, so this is a single
                    // partition over an ordered list.
                    pinned = result.data.filter { it.pinned },
                    others = result.data.filterNot { it.pinned },
                    isLoading = false,
                    failure = null
                )

                is AppResult.Failure -> current.copy(
                    pinned = emptyList(),
                    others = emptyList(),
                    isLoading = false,
                    failure = result.error.toFailure()
                )
            }
        }
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    fun onQueryChange(text: String) {
        query.value = text
        _uiState.update { it.copy(query = text) }
    }

    fun onClearQuery() = onQueryChange("")

    fun onSortChange(newSort: NoteSort) {
        sort.value = newSort
        _uiState.update { it.copy(sort = newSort) }
        viewModelScope.launch { preferencesRepository.setNoteSort(newSort) }
    }

    fun onCreateNote(title: String, content: String) {
        viewModelScope.launch {
            when (val result = noteRepository.create(title, content)) {
                is AppResult.Success -> Unit // the stream refreshes the list
                is AppResult.Failure -> emitMessage(result.error.toMessage())
            }
        }
    }

    fun onUpdateNote(id: Long, title: String, content: String) {
        viewModelScope.launch {
            val result = noteRepository.update(id, title, content)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onTogglePinned(note: Note) {
        viewModelScope.launch {
            val result = noteRepository.setPinned(note.id, !note.pinned)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onDeleteNote(id: Long) {
        viewModelScope.launch {
            when (val result = noteRepository.delete(id)) {
                is AppResult.Success -> {
                    lastDeleted = result.data
                    _events.send(NotesEvent.NoteDeleted(id))
                }

                is AppResult.Failure -> emitMessage(result.error.toMessage())
            }
        }
    }

    /**
     * Puts back the last deleted note with its original id and timestamps.
     * Re-creating it would give it a new identity and move it to the top of the
     * list, which is not what the user asked for.
     */
    fun onUndoDelete() {
        val note = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val result = noteRepository.restore(note)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onRetry() {
        _uiState.update { it.copy(isLoading = true, failure = null) }
        // Bumping the trigger restarts the inner flow through flatMapLatest.
        // Calling observeNotes() again would instead leave two collectors
        // running, each writing to the same state.
        retryTrigger.update { it + 1 }
    }

    private suspend fun emitMessage(text: String) {
        _events.send(NotesEvent.ShowMessage(text))
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 220L
    }
}

// ── Error presentation ───────────────────────────────────────────────────────
// Mapping lives in the presentation layer: the domain reports *what* failed,
// and only the UI decides how to say it.

internal fun DataError.toFailure(): NotesFailure = when (this) {
    is DataError.Unavailable -> NotesFailure(
        title = "No se pudo abrir el almacenamiento",
        body = "Tus notas están a salvo. Cierra la app y vuelve a abrirla."
    )

    is DataError.Corrupted -> NotesFailure(
        title = "Datos ilegibles",
        body = "Esta versión de la app no entiende el formato guardado."
    )

    else -> NotesFailure(
        title = "No se pudieron cargar las notas",
        body = "Vuelve a intentarlo."
    )
}

internal fun DataError.toMessage(): String = when (this) {
    is DataError.Validation -> when (field) {
        DataError.Validation.Field.TITLE -> "Escribe un título"
        DataError.Validation.Field.DUE_DATE -> "Elige una fecha de vencimiento"
    }

    is DataError.NotFound -> "Ese elemento ya no existe"
    is DataError.Conflict -> "Ya existe un elemento con ese identificador"
    is DataError.Unavailable -> "El almacenamiento no está disponible"
    is DataError.Corrupted -> "No se pudieron leer los datos"
    is DataError.Unknown -> "No se pudo completar la acción"
}
