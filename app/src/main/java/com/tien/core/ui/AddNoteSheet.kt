package com.tien.core.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private const val MAX_TITLE   = 120
private const val MAX_CONTENT = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    initialTitle: String = "",
    initialContent: String = "",
    isEditing: Boolean = false,
    onSave: (title: String, content: String) -> Unit
) {
    var title   by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var content by rememberSaveable(initialContent) { mutableStateOf(initialContent) }
    val focus   = LocalFocusManager.current

    val titleError   = title.length > MAX_TITLE
    val canSave      = title.isNotBlank() && !titleError && content.length <= MAX_CONTENT

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { /* custom drag handle via padding */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ── Drag handle pill ─────────────────────────────────────────────
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 20.dp)
                        .height(4.dp)
                        .fillMaxWidth(0.12f)
                        .let {
                            it.then(
                                Modifier.padding(0.dp) // placeholder — actual shape via background
                            )
                        }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                        drawRoundRect(
                            color         = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.4f),
                            cornerRadius  = androidx.compose.ui.geometry.CornerRadius(x = 50f, y = 50f)
                        )
                    }
                }
            }

            Text(
                text  = if (isEditing) "Editar nota" else "Nueva nota",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Title field ───────────────────────────────────────────────────
            OutlinedTextField(
                value         = title,
                onValueChange = { if (it.length <= MAX_TITLE + 1) title = it },
                label         = { Text("Título") },
                placeholder   = { Text("Dale un nombre a tu nota…") },
                isError       = titleError,
                supportingText = {
                    val color = if (titleError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outline
                    Text(
                        text  = "${title.length} / $MAX_TITLE",
                        color = color,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Content field ─────────────────────────────────────────────────
            OutlinedTextField(
                value         = content,
                onValueChange = { if (it.length <= MAX_CONTENT + 1) content = it },
                label         = { Text("Contenido") },
                placeholder   = { Text("Escribe aquí tu nota…") },
                isError       = content.length > MAX_CONTENT,
                supportingText = {
                    val color = if (content.length > MAX_CONTENT) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outline
                    Text(
                        text  = "${content.length} / $MAX_CONTENT",
                        color = color,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                minLines      = 4,
                maxLines      = 8,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focus.clearFocus()
                        if (canSave) {
                            onSave(title.trim(), content.trim())
                            onDismiss()
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Button(
                    onClick  = {
                        onSave(title.trim(), content.trim())
                        onDismiss()
                    },
                    enabled  = canSave
                ) {
                    Text(if (isEditing) "Actualizar" else "Guardar")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
