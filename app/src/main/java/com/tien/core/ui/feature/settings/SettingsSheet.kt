package com.tien.core.ui.feature.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tien.core.domain.model.ThemeMode
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * Appearance settings.
 *
 * Theme choice used to live in `rememberSaveable` at the root, so it survived
 * rotation but not a cold start. It is now a persisted preference, which is why
 * it needs somewhere to be set explicitly rather than a toggle in the app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sheetState: SheetState,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TienTheme.spacing.gutter)
                .navigationBarsPadding()
                .padding(bottom = TienTheme.spacing.section)
        ) {
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(TienTheme.spacing.loose))

            Text(
                text = "Tema",
                style = TienTextStyles.eyebrow,
                color = TienTheme.extendedColors.muted
            )
            Spacer(Modifier.height(TienTheme.spacing.snug))

            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ThemeOptionRow(
                        label = mode.label(),
                        description = mode.description(),
                        selected = mode == themeMode,
                        onSelect = { onThemeModeChange(mode) }
                    )
                }
            }

            // Material You only exists from Android 12, so offering the switch
            // below that would be a control that does nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(TienTheme.spacing.loose))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDynamicColorChange(!dynamicColor) }
                        .padding(vertical = TienTheme.spacing.snug),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Usar colores del fondo de pantalla",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sustituye la paleta de Tien por la de tu sistema.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(TienTheme.spacing.base))
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = TienTheme.spacing.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.base)
    ) {
        RadioButton(
            selected = selected,
            // null: the whole row is the click target, and a nested clickable
            // would make the radio a second, competing one for accessibility.
            onClick = null
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Según el sistema"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.DARK -> "Oscuro"
}

private fun ThemeMode.description(): String = when (this) {
    ThemeMode.SYSTEM -> "Cambia con los ajustes de tu dispositivo."
    ThemeMode.LIGHT -> "Siempre claro."
    ThemeMode.DARK -> "Siempre oscuro."
}
