package com.tien.core.ui.feature.agenda

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.ui.designsystem.component.MetaText
import com.tien.core.ui.designsystem.component.PriorityBadge
import com.tien.core.ui.designsystem.component.TienCard
import com.tien.core.ui.designsystem.component.TienTag
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.core.ui.designsystem.theme.Urgency

/**
 * One task.
 *
 * The leading rail colour is the design's load-bearing element: it turns "how
 * close is this deadline" into something readable at arm's length, so a full
 * agenda can be triaged without parsing a single date string.
 *
 * Colour never carries meaning alone — an overdue task also gets a "Vencida"
 * tag, and the due label spells the state out in words.
 */
@Composable
fun TaskCard(
    item: AgendaTask,
    labels: DateTimeLabels,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = TienTheme.extendedColors
    val task = item.task
    val urgency = item.urgency

    // Completed work recedes rather than disappearing, so the list does not
    // jump under the finger that just ticked the box.
    val contentAlpha by animateFloatAsState(
        targetValue = if (task.isDone) 0.55f else 1f,
        animationSpec = tween(220),
        label = "taskAlpha"
    )

    TienCard(
        modifier = modifier,
        railColor = extended.accentFor(urgency),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = task.isDone,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.semantics {
                    contentDescription = if (task.isDone) {
                        "Marcar ${task.title} como pendiente"
                    } else {
                        "Marcar ${task.title} como completada"
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TienTheme.spacing.tight)
                    .alpha(contentAlpha)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.hasDetails) {
                    Spacer(Modifier.height(TienTheme.spacing.tight))
                    Text(
                        text = task.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(TienTheme.spacing.snug))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
                ) {
                    if (urgency == Urgency.OVERDUE) {
                        TienTag(
                            text = "Vencida",
                            color = extended.overdue,
                            containerColor = extended.overdueContainer,
                            contentDescription = "Tarea vencida"
                        )
                    }
                    MetaText(
                        text = labels.dueLabel(task.dueAt, task.isDone),
                        color = if (urgency == Urgency.OVERDUE) {
                            extended.overdue
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.height(TienTheme.spacing.tight))
                PriorityBadge(priority = task.priority)
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Eliminar ${task.title}",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
