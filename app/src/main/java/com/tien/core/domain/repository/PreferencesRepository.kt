package com.tien.core.domain.repository

import com.tien.core.domain.model.NoteSort
import com.tien.core.domain.model.TaskFilter
import com.tien.core.domain.model.ThemeMode
import com.tien.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/** Read/write access to settings that survive process death. */
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setNoteSort(sort: NoteSort)
    suspend fun setTaskFilter(filter: TaskFilter)
}
