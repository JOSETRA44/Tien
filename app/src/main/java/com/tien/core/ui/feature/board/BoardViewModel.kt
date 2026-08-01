package com.tien.core.ui.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.domain.model.BoardNote
import com.tien.core.domain.model.DEFAULT_BOARD_ID
import com.tien.core.domain.model.PaperColor
import com.tien.core.domain.repository.BoardContent
import com.tien.core.domain.repository.BoardRepository
import com.tien.core.ui.feature.notes.toMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Presentation logic for the board.
 *
 * Deliberately absent from this class: anything that happens *during* a drag.
 * The paper's live position is local UI state inside [PaperNote], and only the
 * final resting place reaches the ViewModel. Routing every frame through here
 * would mean a state emission, a recomposition and a database write per pixel
 * of finger movement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModel(
    private val boardRepository: BoardRepository,
    private val boardId: Long = DEFAULT_BOARD_ID,
    private val random: Random = Random.Default
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    private val _events = Channel<BoardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var lastRemoved: BoardNote? = null

    init {
        retryTrigger
            .flatMapLatest { boardRepository.observeBoard(boardId) }
            .onEach { render(it) }
            .launchIn(viewModelScope)
    }

    private fun render(result: AppResult<BoardContent>) {
        _uiState.update { current ->
            when (result) {
                is AppResult.Success -> current.copy(
                    notes = result.data.notes,
                    links = result.data.links,
                    isLoading = false,
                    failure = null,
                    // A selection pointing at a paper that is no longer on the
                    // wall would leave a toolbar floating over nothing.
                    selectedNoteId = current.selectedNoteId
                        ?.takeIf { id -> result.data.notes.any { it.id == id } },
                    linkSourceId = current.linkSourceId
                        ?.takeIf { id -> result.data.notes.any { it.id == id } }
                )

                is AppResult.Failure -> current.copy(
                    notes = emptyList(),
                    links = emptyList(),
                    isLoading = false,
                    failure = result.error.toBoardFailure()
                )
            }
        }
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    /**
     * Pins a new paper centred on ([boardX], [boardY]).
     *
     * The tilt is drawn here, at the moment of pinning, and then persisted — so
     * every paper is crooked in its own way, and stays that way. Generating it
     * at render time instead would make the whole wall twitch on every redraw.
     */
    fun onPinNote(boardX: Float, boardY: Float, text: String = "", color: PaperColor? = null) {
        viewModelScope.launch {
            val tilt = randomTilt()
            val stock = color ?: randomPaperColor()

            val result = boardRepository.pinNote(
                boardId = boardId,
                text = text,
                // The caller passes the point the paper should sit *around*,
                // which is where the finger was — so offset by half its size.
                x = boardX - BoardNote.DEFAULT_SIZE / 2f,
                y = boardY - BoardNote.DEFAULT_SIZE / 2f,
                rotation = tilt,
                color = stock
            )

            when (result) {
                is AppResult.Failure -> emitMessage(result.error.toMessage())
                is AppResult.Success -> {
                    // A brand-new paper opens straight into the editor: an empty
                    // sheet on the wall is not the goal, writing on it is.
                    if (text.isBlank()) {
                        _uiState.update { it.copy(selectedNoteId = result.data.id) }
                        _events.send(BoardEvent.EditNote(result.data.id))
                    }
                }
            }
        }
    }

    /** Commits a drag. Called once, on release. */
    fun onNoteMoved(id: Long, x: Float, y: Float) {
        val note = _uiState.value.notesById[id] ?: return
        viewModelScope.launch {
            val result = boardRepository.updateTransform(id, x, y, note.rotation)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    /** Raises a paper as it is picked up, so it never drags beneath its neighbours. */
    fun onNotePickedUp(id: Long) {
        viewModelScope.launch { boardRepository.raise(id) }
    }

    fun onNoteTapped(id: Long) {
        val state = _uiState.value
        val linkSource = state.linkSourceId

        when {
            // Second tap of a thread gesture: tie the two together.
            linkSource != null && linkSource != id -> tieThread(linkSource, id)

            // Tapping the source again cancels.
            linkSource == id -> _uiState.update { it.copy(linkSourceId = null) }

            // Tapping the selected paper deselects it.
            state.selectedNoteId == id -> _uiState.update { it.copy(selectedNoteId = null) }

            else -> _uiState.update { it.copy(selectedNoteId = id) }
        }
    }

    private fun tieThread(from: Long, to: Long) {
        _uiState.update { it.copy(linkSourceId = null) }
        viewModelScope.launch {
            val alreadyTied = _uiState.value.isLinked(from, to)
            val result = if (alreadyTied) {
                // Tapping an already-tied pair cuts the thread. The same gesture
                // that made it is the one that removes it.
                boardRepository.unlink(from, to)
            } else {
                boardRepository.link(boardId, from, to)
            }
            if (result is AppResult.Failure) {
                emitMessage(result.error.toMessage())
            } else {
                emitMessage(if (alreadyTied) "Hilo cortado" else "Papeles unidos")
            }
        }
    }

    /** Enters thread mode with [id] as the first paper. */
    fun onStartLink(id: Long) {
        _uiState.update { it.copy(linkSourceId = id, selectedNoteId = null) }
    }

    fun onCancelLink() {
        _uiState.update { it.copy(linkSourceId = null) }
    }

    fun onDeselect() {
        _uiState.update { it.copy(selectedNoteId = null, linkSourceId = null) }
    }

    fun onEditText(id: Long, text: String) {
        viewModelScope.launch {
            val result = boardRepository.updateText(id, text)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    /** Cycles the paper stock, as reaching for the next pad would. */
    fun onCycleColor(id: Long) {
        val note = _uiState.value.notesById[id] ?: return
        viewModelScope.launch {
            val result = boardRepository.updateColor(id, PaperColor.next(note.color))
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onResize(id: Long, width: Float, height: Float) {
        viewModelScope.launch {
            val result = boardRepository.updateSize(
                id,
                width.coerceIn(BoardNote.MIN_SIZE, BoardNote.MAX_SIZE),
                height.coerceIn(BoardNote.MIN_SIZE, BoardNote.MAX_SIZE)
            )
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onRemoveNote(id: Long) {
        viewModelScope.launch {
            when (val result = boardRepository.removeNote(boardId, id)) {
                is AppResult.Success -> {
                    lastRemoved = result.data
                    _uiState.update { it.copy(selectedNoteId = null) }
                    _events.send(BoardEvent.NoteRemoved(id))
                }

                is AppResult.Failure -> emitMessage(result.error.toMessage())
            }
        }
    }

    /** Puts the paper back exactly where it was, tilt and all. */
    fun onUndoRemove() {
        val note = lastRemoved ?: return
        lastRemoved = null
        viewModelScope.launch {
            val result = boardRepository.restoreNote(note)
            if (result is AppResult.Failure) emitMessage(result.error.toMessage())
        }
    }

    fun onRetry() {
        _uiState.update { it.copy(isLoading = true, failure = null) }
        retryTrigger.update { it + 1 }
    }

    private fun randomTilt(): Float =
        (random.nextFloat() * 2f - 1f) * BoardNote.MAX_TILT

    private fun randomPaperColor(): PaperColor =
        PaperColor.entries[random.nextInt(PaperColor.entries.size)]

    private suspend fun emitMessage(text: String) {
        _events.send(BoardEvent.ShowMessage(text))
    }
}

private fun DataError.toBoardFailure(): BoardFailure = when (this) {
    is DataError.Unavailable -> BoardFailure(
        title = "No se pudo abrir la pizarra",
        body = "Tus papeles están a salvo. Cierra la app y vuelve a abrirla."
    )

    is DataError.Corrupted -> BoardFailure(
        title = "Datos ilegibles",
        body = "Esta versión de la app no entiende el formato guardado."
    )

    else -> BoardFailure(
        title = "No se pudo cargar la pizarra",
        body = "Vuelve a intentarlo."
    )
}
