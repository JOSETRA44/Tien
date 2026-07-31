package com.tien.core.ui.feature.notes

import app.cash.turbine.test
import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.domain.model.Note
import com.tien.core.domain.model.NoteSort
import com.tien.core.domain.model.TaskFilter
import com.tien.core.domain.model.ThemeMode
import com.tien.core.domain.model.UserPreferences
import com.tien.core.domain.repository.NoteRepository
import com.tien.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/**
 * Unit tests for the notes ViewModel.
 *
 * These are plain JVM tests with no Android, no SQLite and no JNI, which is the
 * whole point of pushing persistence behind a repository interface — the
 * previous ViewModel called `System.loadLibrary` transitively on construction
 * and could only be exercised on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var notes: FakeNoteRepository
    private lateinit var preferences: FakePreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        notes = FakeNoteRepository()
        preferences = FakePreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = NotesViewModel(notes, preferences)

    @Test
    fun `pinned notes are separated from the rest`() = runTest(dispatcher) {
        notes.emit(
            listOf(
                note(1, "Fijada", pinned = true),
                note(2, "Normal")
            )
        )

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(1L), state.pinned.map { it.id })
        assertEquals(listOf(2L), state.others.map { it.id })
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `a load failure surfaces as a failure state rather than an empty list`() =
        runTest(dispatcher) {
            // The bug this guards against: the old data layer returned "[]" on a
            // hard database error, so the UI rendered "no hay notas todavía".
            notes.emitFailure(DataError.Unavailable)

            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNotNull("A database failure must not look like an empty list", state.failure)
            assertTrue(state.isEmpty)
        }

    @Test
    fun `rapid typing issues a single query`() = runTest(dispatcher) {
        notes.emit(emptyList())
        val vm = viewModel()
        advanceUntilIdle()

        val queriesBefore = notes.queryCount

        "informe".forEach { char ->
            vm.onQueryChange(vm.uiState.value.query + char)
            advanceTimeBy(20)
        }
        advanceUntilIdle()

        // Seven keystrokes, one query — the debounce is doing its job.
        assertEquals(1, notes.queryCount - queriesBefore)
        assertEquals("informe", notes.lastQuery)
    }

    @Test
    fun `deleting emits an undo event and restore preserves identity`() = runTest(dispatcher) {
        val original = note(7, "Comprar pan", pinned = true)
        notes.emit(listOf(original))

        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onDeleteNote(7)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is NotesEvent.NoteDeleted)

            vm.onUndoDelete()
            advanceUntilIdle()

            // Restored with its original id, timestamps and pinned flag — not
            // re-created as a new note at the top of the list.
            assertEquals(original, notes.restored)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank title reports the validation message instead of saving`() =
        runTest(dispatcher) {
            notes.emit(emptyList())
            val vm = viewModel()
            advanceUntilIdle()

            vm.events.test {
                vm.onCreateNote("   ", "cuerpo")
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is NotesEvent.ShowMessage)
                assertEquals("Escribe un título", (event as NotesEvent.ShowMessage).text)
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(notes.created)
        }

    @Test
    fun `changing the sort persists the choice`() = runTest(dispatcher) {
        notes.emit(emptyList())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSortChange(NoteSort.TITLE_ASC)
        advanceUntilIdle()

        assertEquals(NoteSort.TITLE_ASC, preferences.current.value.noteSort)
        assertEquals(NoteSort.TITLE_ASC, notes.lastSort)
    }

    private fun note(id: Long, title: String, pinned: Boolean = false) = Note(
        id = id,
        title = title,
        content = "contenido",
        createdAt = 1_000,
        updatedAt = 2_000,
        pinned = pinned
    )
}

// ── Fakes ────────────────────────────────────────────────────────────────────

private class FakeNoteRepository : NoteRepository {

    private val stream = MutableStateFlow<AppResult<List<Note>>>(AppResult.Success(emptyList()))

    var queryCount = 0
        private set
    var lastQuery: String? = null
        private set
    var lastSort: NoteSort? = null
        private set
    var created: Note? = null
        private set
    var restored: Note? = null
        private set

    fun emit(notes: List<Note>) {
        stream.value = AppResult.Success(notes)
    }

    fun emitFailure(error: DataError) {
        stream.value = AppResult.Failure(error)
    }

    override fun observeNotes(query: String, sort: NoteSort): Flow<AppResult<List<Note>>> {
        queryCount++
        lastQuery = query
        lastSort = sort
        return stream.map { it }
    }

    override suspend fun create(title: String, content: String): AppResult<Note> {
        if (title.isBlank()) {
            return AppResult.Failure(DataError.Validation(DataError.Validation.Field.TITLE))
        }
        val note = Note(99, title.trim(), content, 0, 0, false)
        created = note
        return AppResult.Success(note)
    }

    override suspend fun update(id: Long, title: String, content: String): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun delete(id: Long): AppResult<Note> {
        val existing = (stream.value as? AppResult.Success)?.data?.firstOrNull { it.id == id }
            ?: return AppResult.Failure(DataError.NotFound)
        return AppResult.Success(existing)
    }

    override suspend fun restore(note: Note): AppResult<Unit> {
        restored = note
        return AppResult.Success(Unit)
    }

    override suspend fun findById(id: Long): AppResult<Note> =
        (stream.value as? AppResult.Success)?.data?.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(DataError.NotFound)
}

private class FakePreferencesRepository : PreferencesRepository {
    val current = MutableStateFlow(UserPreferences())

    override val preferences: Flow<UserPreferences> = current

    override suspend fun setThemeMode(mode: ThemeMode) {
        current.value = current.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        current.value = current.value.copy(useDynamicColor = enabled)
    }

    override suspend fun setNoteSort(sort: NoteSort) {
        current.value = current.value.copy(noteSort = sort)
    }

    override suspend fun setTaskFilter(filter: TaskFilter) {
        current.value = current.value.copy(taskFilter = filter)
    }
}
