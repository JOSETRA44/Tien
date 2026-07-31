package com.tien.core.data.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tien.core.domain.model.NoteSort
import com.tien.core.domain.model.TaskFilter
import com.tien.core.domain.model.ThemeMode
import com.tien.core.domain.model.UserPreferences
import com.tien.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * [PreferencesRepository] backed by Jetpack DataStore.
 *
 * Values are stored by enum **name**, not by ordinal: reordering an enum would
 * otherwise silently repoint every stored setting at a different value.
 */
internal class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            // A corrupt preferences file must not take the app down with it —
            // fall back to defaults and carry on.
            if (throwable is IOException) {
                Log.e(TAG, "Failed to read preferences, using defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { stored ->
            UserPreferences(
                themeMode = stored[Keys.THEME_MODE].toEnumOr(ThemeMode.DEFAULT),
                useDynamicColor = stored[Keys.DYNAMIC_COLOR] ?: false,
                noteSort = stored[Keys.NOTE_SORT].toEnumOr(NoteSort.DEFAULT),
                taskFilter = stored[Keys.TASK_FILTER].toEnumOr(TaskFilter.DEFAULT)
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) = edit {
        it[Keys.THEME_MODE] = mode.name
    }

    override suspend fun setDynamicColor(enabled: Boolean) = edit {
        it[Keys.DYNAMIC_COLOR] = enabled
    }

    override suspend fun setNoteSort(sort: NoteSort) = edit {
        it[Keys.NOTE_SORT] = sort.name
    }

    override suspend fun setTaskFilter(filter: TaskFilter) = edit {
        it[Keys.TASK_FILTER] = filter.name
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (error: IOException) {
            // Losing a preference write is a cosmetic failure; crashing on it
            // would not be.
            Log.e(TAG, "Failed to persist preference", error)
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTE_SORT = stringPreferencesKey("note_sort")
        val TASK_FILTER = stringPreferencesKey("task_filter")
    }

    private companion object {
        const val TAG = "PreferencesRepository"
    }
}

/**
 * Resolves a stored enum name, falling back when the value is absent or was
 * written by a build that knew an entry this one does not.
 */
private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback
