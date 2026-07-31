package com.tien.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tien.core.ui.SettingsViewModel
import com.tien.core.ui.TienApp
import com.tien.core.ui.TienViewModelFactory
import com.tien.core.ui.designsystem.theme.TienTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TienApplication).container

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = TienViewModelFactory.settings(container)
            )
            // Read at the root so the whole tree recomposes with the new scheme
            // the moment the preference changes — and, unlike the previous
            // `rememberSaveable` toggle, the choice survives a cold start.
            val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()

            TienTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.useDynamicColor
            ) {
                TienApp(
                    container = container,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
