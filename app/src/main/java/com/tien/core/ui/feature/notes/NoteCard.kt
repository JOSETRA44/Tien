package com.tien.core.ui.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.domain.model.Note
import com.tien.core.ui.designsystem.component.MetaText
import com.tien.core.ui.designsystem.component.TienCard
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * One note in the list.
 *
 * Carries no urgency rail — notes have no deadline, and spending the signature
 * device on them would drain it of meaning by the time the agenda needs it.
 */
@Composable
fun NoteCard(
    note: Note,
    labels: DateTimeLabels,
    onEdit: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keyed on the note id so recycling a row in the lazy list cannot leave the
    // previous row's menu open.
    var menuExpanded by remember(note.id) { mutableStateOf(false) }

    TienCard(
        modifier = modifier,
        onClick = { onEdit(note) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )

            if (note.pinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Fijada",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically)
                )
                Spacer(Modifier.size(TienTheme.spacing.snug))
            }

            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Opciones de ${note.title}"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (note.pinned) "Quitar de fijadas" else "Fijar arriba") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (note.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onTogglePin(note)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Editar") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit(note)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete(note)
                    }
                )
            }
        }

        if (note.hasBody) {
            Spacer(Modifier.height(TienTheme.spacing.tight))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(TienTheme.spacing.base))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetaText(
                text = labels.relative(note.updatedAt),
                // "Hace 5 min" alone is ambiguous out of context.
                modifier = Modifier.semantics {
                    contentDescription = "Editada ${labels.relative(note.updatedAt)}"
                }
            )
        }
    }
}
