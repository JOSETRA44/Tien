package com.tien.core.ui.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * Writing on a paper.
 *
 * **Saves as you type, not on a button.** A pen does not have a commit step, and
 * a sheet you scribbled on keeps what you wrote whether or not you remember to
 * confirm it. `onSave` fires on every change; the write is a single indexed
 * UPDATE of one column, so this is cheap.
 *
 * There is no title field. A paper on a wall is a thought, and forcing a
 * heading onto it would make it a document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperEditorSheet(
    sheetState: SheetState,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    // Opens straight into the text: the sheet exists to capture a thought, and
    // making the user tap once more to start writing wastes the moment.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TienTheme.spacing.gutter)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = TienTheme.spacing.loose)
        ) {
            Text(
                text = "Escribe en el papel",
                style = TienTextStyles.eyebrow,
                color = TienTheme.extendedColors.muted
            )

            Spacer(Modifier.height(TienTheme.spacing.snug))

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onSave(it)
                },
                placeholder = { Text("Tu idea…") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .focusRequester(focusRequester)
            )

            Spacer(Modifier.height(TienTheme.spacing.comfy))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = "Quitar de la pared",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // "Listo", not "Guardar": the text is already saved, so calling
                // it Save would imply this is the moment it persists.
                Button(onClick = onDismiss) {
                    Text("Listo")
                }
            }
        }
    }
}
