package com.tien.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tien.core.ui.NotesScreen
import com.tien.core.ui.theme.TienTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

            TienTheme(darkTheme = darkTheme) {
                NotesScreen(
                    isDarkTheme = darkTheme,
                    onThemeToggle = { darkTheme = !darkTheme }
                )
            }
        }
    }
}
