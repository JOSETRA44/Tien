package com.tien.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.domain.model.ThemeMode
import com.tien.core.domain.model.UserPreferences
import com.tien.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the appearance settings.
 *
 * Separate from the feature ViewModels because the theme is app-wide state: it
 * is read by the root composable before any screen exists.
 */
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            // WhileSubscribed with a 5s grace period: a configuration change
            // drops the last subscriber for a few frames, and restarting the
            // DataStore read every rotation would flash the default theme.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }
}
