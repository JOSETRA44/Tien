package com.tien.core.ui.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tien.core.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Pine600,
    onPrimary = Color.White,
    primaryContainer = Pine100,
    onPrimaryContainer = Pine900,

    secondary = Pine500,
    onSecondary = Color.White,
    secondaryContainer = Pine050,
    onSecondaryContainer = Pine800,

    // Tertiary is the only warm role in the light scheme, and it is reserved
    // for priority emphasis.
    tertiary = Ochre600,
    onTertiary = Color.White,
    tertiaryContainer = Ochre100,
    onTertiaryContainer = Color(0xFF3D2C10),

    background = Paper,
    onBackground = Graphite900,
    surface = Paper,
    onSurface = Graphite900,
    surfaceVariant = PaperSunk,
    onSurfaceVariant = Graphite500,
    surfaceContainer = PaperSunk,
    surfaceContainerHigh = Color(0xFFEDECE6),
    surfaceContainerLow = Color(0xFFF7F6F1),

    outline = Graphite300,
    outlineVariant = PaperEdge,

    error = Clay600,
    onError = Color.White,
    errorContainer = Clay100,
    onErrorContainer = Color(0xFF3F110A),

    inverseSurface = Graphite800,
    inverseOnSurface = Paper,
    inversePrimary = Pine300
)

private val DarkColors = darkColorScheme(
    primary = Pine300,
    onPrimary = Pine900,
    primaryContainer = Pine700,
    onPrimaryContainer = Pine050,

    secondary = Pine200,
    onSecondary = Pine900,
    secondaryContainer = Pine800,
    onSecondaryContainer = Pine050,

    tertiary = OchreDark,
    onTertiary = Color(0xFF3D2C10),
    tertiaryContainer = Color(0xFF5A4116),
    onTertiaryContainer = Ochre100,

    background = Graphite900,
    onBackground = Color(0xFFE6E9E6),
    surface = Graphite900,
    onSurface = Color(0xFFE6E9E6),
    surfaceVariant = Graphite700,
    onSurfaceVariant = Graphite300,
    surfaceContainer = Graphite800,
    surfaceContainerHigh = Graphite700,
    surfaceContainerLow = Color(0xFF181B19),

    outline = Graphite400,
    outlineVariant = Graphite700,

    error = ClayDark,
    onError = Color(0xFF52140B),
    errorContainer = Color(0xFF73281B),
    onErrorContainer = Clay100,

    inverseSurface = Paper,
    inverseOnSurface = Graphite900,
    inversePrimary = Pine600
)

/**
 * Root theme.
 *
 * @param themeMode user preference; [ThemeMode.SYSTEM] defers to the device.
 * @param dynamicColor opt **in** to Material You. It previously defaulted to
 *   `true`, which meant the hand-tuned palette above was dead code on every
 *   Android 12+ device — the app looked like whatever wallpaper the user had.
 *   Off by default, available to anyone who wants it.
 */
@Composable
fun TienTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme = when {
        supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // Both bars, not just the status bar: under edge-to-edge the
            // gesture pill draws over app content and needs the same treatment.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalTienExtendedColors provides extendedColors,
        LocalTienSpacing provides TienSpacing(),
        LocalTienElevation provides TienElevation()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TienTypography,
            shapes = TienShapes,
            content = content
        )
    }
}

/** Accessors so call sites read `TienTheme.spacing.gutter`. */
object TienTheme {
    val extendedColors: TienExtendedColors
        @Composable get() = LocalTienExtendedColors.current

    val spacing: TienSpacing
        @Composable get() = LocalTienSpacing.current

    val elevation: TienElevation
        @Composable get() = LocalTienElevation.current
}
