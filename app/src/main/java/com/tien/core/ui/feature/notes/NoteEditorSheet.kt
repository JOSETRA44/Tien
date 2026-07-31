package com.tien.core.ui.feature.notes

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * Create/edit sheet for a note.
 *
 * The draft lives here, in `rememberSaveable` **strings**, rather than in the
 * caller. Hoisting it any higher was what made the previous screen fragile:
 * a `Note` object was being stored in `rememberSaveable`, and since `Note` is
 * not `Parcelable` the state restoration machinery rejected it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
    sheetState: SheetState,
    initialTitle: String,
    initialContent: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var content by rememberSaveable(initialContent) { mutableStateOf(initialContent) }
    var titleTouched by rememberSaveable { mutableStateOf(false) }

    val titleFocus = remember { FocusRequester() }

    // A new note opens straight into the title field: the point of the sheet is
    // to capture a thought before it is gone.
    LaunchedEffect(isEditing) {
        if (!isEditing) titleFocus.requestFocus()
    }

    val titleIsBlank = title.isBlank()
    val showTitleError = titleTouched && titleIsBlank

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TienTheme.spacing.gutter)
                // imePadding keeps the buttons above the keyboard; without it
                // the save action sits behind it on short screens.
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = TienTheme.spacing.loose)
        ) {
            Text(
                text = if (isEditing) "Editar nota" else "Nueva nota",
                style = TienTextStyles.eyebrow.copy(),
                color = TienTheme.extendedColors.muted
            )

            Spacer(Modifier.height(TienTheme.spacing.snug))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleTouched = true
                },
                label = { Text("Título") },
                singleLine = true,
                isError = showTitleError,
                supportingText = if (showTitleError) {
                    { Text("Escribe un título") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
            )

            Spacer(Modifier.height(TienTheme.spacing.base))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Contenido") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 320.dp)
            )

            Spacer(Modifier.height(TienTheme.spacing.loose))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    TienTheme.spacing.snug,
                    Alignment.End
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        titleTouched = true
                        if (!titleIsBlank) {
                            onSave(title, content)
                            onDismiss()
                        }
                    },
                    // Enabled even when invalid, so tapping it explains what is
                    // missing. A disabled button that never says why is a
                    // dead end.
                    enabled = true
                ) {
                    // The label matches the outcome: "Guardar" produces a saved
                    // note, in both modes.
                    Text(if (isEditing) "Guardar cambios" else "Guardar nota")
                }
            }
        }
    }
}
