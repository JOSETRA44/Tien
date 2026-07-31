package com.tien.core.domain.model

/** How the app decides between the light and dark colour schemes. */
enum class ThemeMode {
    /** Follow the device setting. */
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM
    }
}

/**
 * User-controlled settings that outlive the process.
 *
 * These used to live in `rememberSaveable` inside the root composable, so they
 * survived rotation but were lost the moment the app was killed — the user had
 * to re-pick dark mode on every cold start.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DEFAULT,

    /**
     * Whether to derive the palette from the wallpaper (Material You, API 31+).
     *
     * Defaults to **off** so the app's own identity is what ships. It was
     * previously hard-coded on, which meant the hand-tuned brand palette was
     * dead code on every Android 12+ device.
     */
    val useDynamicColor: Boolean = false,

    val noteSort: NoteSort = NoteSort.DEFAULT,
    val taskFilter: TaskFilter = TaskFilter.DEFAULT
)
