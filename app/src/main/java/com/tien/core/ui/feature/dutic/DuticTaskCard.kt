package com.tien.core.ui.feature.dutic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.ui.designsystem.component.MetaText
import com.tien.core.ui.designsystem.component.TienTag
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.core.ui.designsystem.theme.Urgency
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.model.SubmissionStatus

/**
 * One assignment from the aula virtual.
 *
 * ### Two signals, two channels
 * A university assignment *is* a deadline, so it keeps the app's urgency rail —
 * the same colour language the agenda uses. Inventing a second one would teach
 * the student two vocabularies for the same idea.
 *
 * "Hidden" is a different fact and needs a different channel, so it gets a
 * **dashed outline**: in almost every visual language a dashed line reads as
 * provisional or unofficial, which is exactly what this is — work that was never
 * on the official list. It sits alongside the rail rather than fighting it, and
 * the "OCULTA" tag carries the meaning in words for anyone who cannot rely on
 * the line style.
 */
@Composable
fun DuticTaskCard(
    task: DuticTask,
    labels: DateTimeLabels,
    nowEpochSeconds: Long,
    todayEndEpochSeconds: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = TienTheme.extendedColors
    val urgency = task.toUrgency(nowEpochSeconds, todayEndEpochSeconds)
    val railColor = extended.accentFor(urgency)
    val shape = MaterialTheme.shapes.large

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append(task.name)
                    append(", ")
                    append(task.courseName)
                    if (task.hidden) append(", oculta en el calendario")
                }
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = TienTheme.elevation.flat
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                // Solid for work the calendar listed, dashed for what it hid.
                .drawBehind {
                    drawBorderFor(
                        hidden = task.hidden,
                        color = if (task.hidden) railColor else extended.hairline,
                        cornerRadiusPx = CORNER_RADIUS_DP.dp.toPx(),
                        strokeWidthPx = 1.5.dp.toPx()
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = CORNER_RADIUS_DP.dp,
                            bottomStart = CORNER_RADIUS_DP.dp
                        )
                    )
                    .background(railColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TienTheme.spacing.comfy,
                        end = TienTheme.spacing.comfy,
                        top = TienTheme.spacing.base,
                        bottom = TienTheme.spacing.base
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (task.hidden) {
                        TienTag(
                            text = "Oculta",
                            color = extended.overdue,
                            containerColor = extended.overdueContainer,
                            contentDescription = "El calendario no muestra esta tarea"
                        )
                    }
                }

                Spacer(Modifier.height(TienTheme.spacing.tight))

                MetaText(text = task.courseName)

                Spacer(Modifier.height(TienTheme.spacing.snug))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
                ) {
                    MetaText(
                        text = task.deadlineLabel(labels),
                        color = if (urgency == Urgency.OVERDUE) {
                            extended.overdue
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // Only stated when Moodle actually told us. "Sin entregar"
                    // on an assignment whose state we never resolved would be a
                    // guess presented as a fact.
                    task.submissionLabel()?.let { label ->
                        MetaText(text = "·")
                        MetaText(text = label)
                    }
                }

                // The alert that justifies the whole scrape: the brief names a
                // date that is not the one Moodle enforces.
                if (task.dateConflict) {
                    Spacer(Modifier.height(TienTheme.spacing.snug))
                    TienTag(
                        text = "Fechas que no coinciden",
                        color = extended.today,
                        containerColor = extended.todayContainer,
                        contentDescription = CONFLICT_DESCRIPTION
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBorderFor(
    hidden: Boolean,
    color: Color,
    cornerRadiusPx: Float,
    strokeWidthPx: Float
) {
    drawRoundRect(
        color = if (hidden) color.copy(alpha = 0.7f) else color,
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        size = Size(size.width, size.height),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = if (hidden) {
                PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))
            } else {
                null
            }
        )
    )
}

/**
 * Maps an assignment onto the app's shared urgency scale.
 *
 * `dueDate` is copied into a local first: it is a public property of another
 * module, so Kotlin cannot smart-cast it after the null check — that module is
 * free to change it into something computed without recompiling this one.
 */
internal fun DuticTask.toUrgency(
    nowEpochSeconds: Long,
    todayEndEpochSeconds: Long
): Urgency {
    if (submission == SubmissionStatus.GRADED || submission == SubmissionStatus.SUBMITTED) {
        return Urgency.DONE
    }

    val due = dueDate ?: return Urgency.SCHEDULED

    return when {
        due < nowEpochSeconds -> Urgency.OVERDUE
        due < todayEndEpochSeconds -> Urgency.TODAY
        due - nowEpochSeconds <= SOON_WINDOW_SECONDS -> Urgency.SOON
        else -> Urgency.SCHEDULED
    }
}

private fun DuticTask.deadlineLabel(labels: DateTimeLabels): String {
    val due = dueDate ?: return "Sin fecha"
    val isDone = submission == SubmissionStatus.SUBMITTED ||
        submission == SubmissionStatus.GRADED
    return labels.dueLabel(due, isDone = isDone)
        .ifBlank { labels.dateTime(due) }
}

private fun DuticTask.submissionLabel(): String? = when (submission) {
    SubmissionStatus.NOT_SUBMITTED -> "Sin entregar"
    SubmissionStatus.SUBMITTED -> "Entregada"
    SubmissionStatus.GRADED -> grade?.let { "Nota $it" } ?: "Calificada"
    // Unknown means the sweep has not resolved it yet — say nothing rather than
    // assert something that may be wrong.
    SubmissionStatus.UNKNOWN -> null
}

private const val CONFLICT_DESCRIPTION =
    "La consigna menciona una fecha distinta a la oficial"

private const val CORNER_RADIUS_DP = 18
private const val DASH_ON = 9f
private const val DASH_OFF = 7f
private const val SOON_WINDOW_SECONDS = 24L * 60 * 60
