package com.tien.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.NativeLib
import com.tien.core.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── UI State ──────────────────────────────────────────────────────────────────

enum class NotesSort {
    NEWEST,
    OLDEST,
    TITLE
}

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val sort: NotesSort = NotesSort.NEWEST,
    val selectedDayKey: String? = null
) {
    /** Notes filtered by search + date and sorted using the selected mode. */
    val filtered: List<Note>
        get() {
            val searched = if (searchQuery.isBlank()) notes else notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
            }

            val byDay = if (selectedDayKey == null) searched else searched.filter {
                toDayKey(it.timestamp) == selectedDayKey
            }

            return when (sort) {
                NotesSort.NEWEST -> byDay.sortedByDescending { it.timestamp }
                NotesSort.OLDEST -> byDay.sortedBy { it.timestamp }
                NotesSort.TITLE -> byDay.sortedBy { it.title.lowercase(Locale.getDefault()) }
            }
        }

    val availableDayKeys: List<String>
        get() {
            val searched = if (searchQuery.isBlank()) notes else notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
            }
            return searched
                .map { toDayKey(it.timestamp) }
                .distinct()
                .sortedDescending()
        }

    val groupedByDay: List<Pair<String, List<Note>>>
        get() = filtered
            .groupBy { toDayKey(it.timestamp) }
            .toList()
            .sortedByDescending { it.first }
}

fun formatDayLabel(dayKey: String): String {
    return try {
        val locale = Locale.getDefault()
        val input = SimpleDateFormat("yyyy-MM-dd", locale)
        val output = SimpleDateFormat("EEE, d MMM yyyy", locale)
        val parsed = input.parse(dayKey) ?: return dayKey
        output.format(parsed).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        dayKey
    }
}

private fun toDayKey(epochSeconds: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1_000L))
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val dbPath: String = app.getDatabasePath("tien_notes.db").absolutePath

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val opened = NativeLib.createDb(dbPath)
            if (!opened) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No se pudo abrir la base de datos.") }
                return@launch
            }
            loadNotes()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun insertNote(title: String, content: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El título no puede estar vacío.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.insertNote(dbPath, trimmedTitle, content.trim())
            if (ok) {
                loadNotes()
            } else {
                _uiState.update { it.copy(errorMessage = "Error al guardar la nota.") }
            }
        }
    }

    fun updateNote(id: Long, title: String, content: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El título no puede estar vacío.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.updateNote(dbPath, id, trimmedTitle, content.trim())
            if (ok) {
                loadNotes()
            } else {
                _uiState.update { it.copy(errorMessage = "No se pudo actualizar la nota.") }
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.deleteNote(dbPath, id)
            if (ok) {
                loadNotes()
            } else {
                _uiState.update { it.copy(errorMessage = "No se pudo eliminar la nota.") }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val available = availableDaysFor(current.notes, query)
            val selected = current.selectedDayKey?.takeIf { it in available }
            current.copy(searchQuery = query, selectedDayKey = selected)
        }
    }

    fun setSort(sort: NotesSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun setSelectedDay(dayKey: String?) {
        _uiState.update { it.copy(selectedDayKey = dayKey) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun loadNotes() {
        val json = withContext(Dispatchers.IO) { NativeLib.getNotes(dbPath) }
        val notes = parseNotes(json)
        _uiState.update { current ->
            val available = availableDaysFor(notes, current.searchQuery)
            val selected = current.selectedDayKey?.takeIf { it in available }
            current.copy(notes = notes, isLoading = false, selectedDayKey = selected)
        }
    }

    private fun availableDaysFor(notes: List<Note>, query: String): List<String> {
        val searched = if (query.isBlank()) notes else notes.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
        }
        return searched
            .map { toDayKey(it.timestamp) }
            .distinct()
            .sortedDescending()
    }

    /**
     * Parses the JSON array returned by [NativeLib.getNotes] into a list of [Note].
     * Uses the built-in [org.json.JSONArray] — zero extra dependencies.
     *
     * Expected shape: [{"id":1,"title":"…","content":"…","timestamp":1234567890}, …]
     * Returns an empty list on any parse error (logged to avoid crashing the UI).
     */
    private fun parseNotes(json: String): List<Note> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Note(
                    id        = obj.getLong("id"),
                    title     = obj.getString("title"),
                    content   = obj.getString("content"),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Error al leer las notas.") }
            emptyList()
        }
    }
}
