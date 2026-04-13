package com.tien.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.NativeLib
import com.tien.core.model.Note
import com.tien.core.model.TaskItem
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

enum class NotesSort {
    NEWEST,
    OLDEST,
    TITLE
}

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val sort: NotesSort = NotesSort.NEWEST,
    val selectedDayKey: String? = null
) {
    val filteredNotes: List<Note>
        get() {
            val searched = if (searchQuery.isBlank()) notes else notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
            }
            return when (sort) {
                NotesSort.NEWEST -> searched.sortedByDescending { it.timestamp }
                NotesSort.OLDEST -> searched.sortedBy { it.timestamp }
                NotesSort.TITLE -> searched.sortedBy { it.title.lowercase(Locale.getDefault()) }
            }
        }

    val agendaTasks: List<TaskItem>
        get() {
            val searched = if (searchQuery.isBlank()) tasks else tasks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.details.contains(searchQuery, ignoreCase = true)
            }
            val byDay = selectedDayKey?.let { key ->
                searched.filter { toDayKey(it.dueAt) == key }
            } ?: searched
            return byDay.sortedBy { it.dueAt }
        }

    val taskDays: List<String>
        get() = tasks
            .map { toDayKey(it.dueAt) }
            .distinct()
            .sorted()
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

fun formatDateTime(epochSeconds: Long): String {
    val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1_000L))
}

private fun toDayKey(epochSeconds: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1_000L))
}

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
            loadAll()
        }
    }

    fun insertNote(title: String, content: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El título no puede estar vacío.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.insertNote(dbPath, trimmedTitle, content.trim())
            if (ok) loadAll() else _uiState.update { it.copy(errorMessage = "Error al guardar la nota.") }
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
            if (ok) loadAll() else _uiState.update { it.copy(errorMessage = "No se pudo actualizar la nota.") }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.deleteNote(dbPath, id)
            if (ok) loadAll() else _uiState.update { it.copy(errorMessage = "No se pudo eliminar la nota.") }
        }
    }

    fun insertTask(title: String, details: String, dueAt: Long, priority: Int) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El título de la tarea no puede estar vacío.") }
            return
        }
        if (dueAt <= 0) {
            _uiState.update { it.copy(errorMessage = "Fecha de vencimiento inválida.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.insertTask(dbPath, trimmedTitle, details.trim(), dueAt, priority.coerceIn(0, 2))
            if (ok) loadAll() else _uiState.update { it.copy(errorMessage = "No se pudo guardar la tarea.") }
        }
    }

    fun toggleTaskDone(id: Long, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.toggleTaskDone(dbPath, id, done)
            if (ok) loadAll() else _uiState.update { it.copy(errorMessage = "No se pudo actualizar la tarea.") }
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = NativeLib.deleteTask(dbPath, id)
            if (ok) loadAll() else _uiState.update { it.copy(errorMessage = "No se pudo eliminar la tarea.") }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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

    private suspend fun loadAll() {
        val notesJson = withContext(Dispatchers.IO) { NativeLib.getNotes(dbPath) }
        val tasksJson = withContext(Dispatchers.IO) { NativeLib.getTasks(dbPath) }
        val notes = parseNotes(notesJson)
        val tasks = parseTasks(tasksJson)
        _uiState.update { current ->
            val selected = current.selectedDayKey?.takeIf { it in tasks.map { task -> toDayKey(task.dueAt) } }
            current.copy(notes = notes, tasks = tasks, isLoading = false, selectedDayKey = selected)
        }
    }

    private fun parseNotes(json: String): List<Note> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Note(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Error al leer las notas.") }
            emptyList()
        }
    }

    private fun parseTasks(json: String): List<TaskItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                TaskItem(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    details = obj.optString("details", ""),
                    dueAt = obj.getLong("dueAt"),
                    createdAt = obj.optLong("createdAt", 0L),
                    priority = obj.optInt("priority", 1),
                    isDone = obj.optBoolean("isDone", false)
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Error al leer las tareas.") }
            emptyList()
        }
    }
}
