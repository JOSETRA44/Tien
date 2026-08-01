package com.tien.core.ui.feature.board

import app.cash.turbine.test
import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.domain.model.BoardLink
import com.tien.core.domain.model.BoardNote
import com.tien.core.domain.model.PaperColor
import com.tien.core.domain.repository.BoardContent
import com.tien.core.domain.repository.BoardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeBoardRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeBoardRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Seeded, so the tilt is random in production but deterministic here.
    private fun viewModel(seed: Int = 42) =
        BoardViewModel(repository, boardId = BOARD_ID, random = Random(seed))

    @Test
    fun `pinned papers are tilted within the allowed range`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repeat(20) { i ->
            vm.onPinNote(boardX = i * 10f, boardY = 0f)
            advanceUntilIdle()
        }

        assertEquals(20, repository.pinned.size)
        repository.pinned.forEach { pin ->
            assertTrue(
                "tilt ${pin.rotation} outside ±${BoardNote.MAX_TILT}",
                abs(pin.rotation) <= BoardNote.MAX_TILT
            )
        }
        // Papers must not all hang at the same angle — that would look printed.
        assertTrue(repository.pinned.map { it.rotation }.distinct().size > 1)
    }

    /**
     * The caller passes the point the paper should sit around; the note is
     * stored by its top-left corner. Getting this wrong pins every paper down
     * and to the right of the tap.
     */
    @Test
    fun `a pinned paper is centred on the requested point`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPinNote(boardX = 500f, boardY = 300f)
        advanceUntilIdle()

        val pin = repository.pinned.single()
        assertEquals(500f - BoardNote.DEFAULT_SIZE / 2f, pin.x, 0.01f)
        assertEquals(300f - BoardNote.DEFAULT_SIZE / 2f, pin.y, 0.01f)
    }

    @Test
    fun `tapping a second paper while linking ties them`() = runTest(dispatcher) {
        repository.emit(listOf(note(1), note(2)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onStartLink(1)
        vm.onNoteTapped(2)
        advanceUntilIdle()

        assertEquals(1L to 2L, repository.linked)
        assertNull("link mode must end after tying", vm.uiState.value.linkSourceId)
    }

    /** The gesture that ties a thread is the one that cuts it. */
    @Test
    fun `tapping an already tied pair cuts the thread`() = runTest(dispatcher) {
        repository.emit(
            notes = listOf(note(1), note(2)),
            links = listOf(BoardLink(id = 9, boardId = BOARD_ID, fromNoteId = 1, toNoteId = 2, createdAt = 0))
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onStartLink(1)
        vm.onNoteTapped(2)
        advanceUntilIdle()

        assertEquals(1L to 2L, repository.unlinked)
        assertNull(repository.linked)
    }

    @Test
    fun `tapping the link source again cancels`() = runTest(dispatcher) {
        repository.emit(listOf(note(1)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onStartLink(1)
        vm.onNoteTapped(1)
        advanceUntilIdle()

        assertNull(vm.uiState.value.linkSourceId)
        assertNull(repository.linked)
    }

    /** Undo has to put the paper back where it was, not make a new one. */
    @Test
    fun `undo restores the paper with its position and tilt intact`() = runTest(dispatcher) {
        val original = note(5).copy(x = 123f, y = 456f, rotation = -3.7f, z = 8)
        repository.emit(listOf(original))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onRemoveNote(5)
            advanceUntilIdle()
            assertTrue(awaitItem() is BoardEvent.NoteRemoved)

            vm.onUndoRemove()
            advanceUntilIdle()

            assertEquals(original, repository.restored)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving a paper keeps its tilt`() = runTest(dispatcher) {
        repository.emit(listOf(note(3).copy(rotation = 4.2f)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNoteMoved(3, x = 900f, y = 100f)
        advanceUntilIdle()

        val moved = repository.transformed
        assertNotNull(moved)
        assertEquals(900f, moved!!.second, 0.01f)
        // A drag moves a paper; it does not straighten it.
        assertEquals(4.2f, moved.third, 0.01f)
    }

    @Test
    fun `a selection pointing at a removed paper is dropped`() = runTest(dispatcher) {
        repository.emit(listOf(note(1), note(2)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNoteTapped(1)
        assertEquals(1L, vm.uiState.value.selectedNoteId)

        // The paper disappears — deleted on another surface, say.
        repository.emit(listOf(note(2)))
        advanceUntilIdle()

        assertNull(
            "a toolbar must not float over a paper that is gone",
            vm.uiState.value.selectedNoteId
        )
    }

    @Test
    fun `a load failure surfaces instead of showing an empty wall`() = runTest(dispatcher) {
        repository.emitFailure(DataError.Unavailable)
        val vm = viewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.failure)
        assertTrue(vm.uiState.value.isEmpty)
    }

    private fun note(id: Long) = BoardNote(
        id = id,
        boardId = BOARD_ID,
        text = "idea $id",
        x = 0f,
        y = 0f,
        width = BoardNote.DEFAULT_SIZE,
        height = BoardNote.DEFAULT_SIZE,
        rotation = 0f,
        color = PaperColor.CREAM,
        z = id.toInt(),
        sourceNoteId = null,
        createdAt = 0,
        updatedAt = 0
    )

    private companion object {
        const val BOARD_ID = 1L
    }
}

// ── Fake ─────────────────────────────────────────────────────────────────────

private class FakeBoardRepository : BoardRepository {

    private val stream = MutableStateFlow<AppResult<BoardContent>>(
        AppResult.Success(BoardContent())
    )

    data class Pin(val x: Float, val y: Float, val rotation: Float)

    val pinned = mutableListOf<Pin>()
    var linked: Pair<Long, Long>? = null
        private set
    var unlinked: Pair<Long, Long>? = null
        private set
    var restored: BoardNote? = null
        private set
    var transformed: Triple<Long, Float, Float>? = null
        private set

    fun emit(notes: List<BoardNote>, links: List<BoardLink> = emptyList()) {
        stream.value = AppResult.Success(BoardContent(notes, links))
    }

    fun emitFailure(error: DataError) {
        stream.value = AppResult.Failure(error)
    }

    override fun observeBoard(boardId: Long): Flow<AppResult<BoardContent>> = stream

    override suspend fun pinNote(
        boardId: Long,
        text: String,
        x: Float,
        y: Float,
        rotation: Float,
        color: PaperColor
    ): AppResult<BoardNote> {
        pinned += Pin(x, y, rotation)
        val note = BoardNote(
            id = pinned.size.toLong(),
            boardId = boardId,
            text = text,
            x = x,
            y = y,
            width = BoardNote.DEFAULT_SIZE,
            height = BoardNote.DEFAULT_SIZE,
            rotation = rotation,
            color = color,
            z = pinned.size,
            sourceNoteId = null,
            createdAt = 0,
            updatedAt = 0
        )
        return AppResult.Success(note)
    }

    override suspend fun updateText(id: Long, text: String) = AppResult.Success(Unit)

    override suspend fun updateTransform(
        id: Long,
        x: Float,
        y: Float,
        rotation: Float
    ): AppResult<Unit> {
        // The rotation is carried in the third slot so a test can assert the
        // tilt survived the move.
        transformed = Triple(id, x, rotation)
        return AppResult.Success(Unit)
    }

    override suspend fun updateSize(id: Long, width: Float, height: Float) = AppResult.Success(Unit)

    override suspend fun updateColor(id: Long, color: PaperColor) = AppResult.Success(Unit)

    override suspend fun raise(id: Long) = AppResult.Success(Unit)

    override suspend fun removeNote(boardId: Long, id: Long): AppResult<BoardNote> {
        val existing = (stream.value as? AppResult.Success)?.data?.notes?.firstOrNull { it.id == id }
            ?: return AppResult.Failure(DataError.NotFound)
        return AppResult.Success(existing)
    }

    override suspend fun restoreNote(note: BoardNote): AppResult<Unit> {
        restored = note
        return AppResult.Success(Unit)
    }

    override suspend fun link(boardId: Long, fromNoteId: Long, toNoteId: Long): AppResult<Unit> {
        linked = fromNoteId to toNoteId
        return AppResult.Success(Unit)
    }

    override suspend fun unlink(fromNoteId: Long, toNoteId: Long): AppResult<Unit> {
        unlinked = fromNoteId to toNoteId
        return AppResult.Success(Unit)
    }
}
