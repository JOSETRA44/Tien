package com.tien.core.ui.feature.dutic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.component.MetaText
import com.tien.core.ui.designsystem.component.SectionEyebrow
import com.tien.core.ui.designsystem.component.TienTag
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.repository.AssignmentDetail

/**
 * What an assignment actually asks for.
 *
 * Reaches `get_assignment_detail`. Before this, tapping a task card did nothing
 * — a control that looks pressable and answers with silence, which is worse than
 * no affordance at all.
 *
 * The brief is the point. A student looking at "Informe de laboratorio" needs
 * the instructions, the rubric attached to it, and whether the teacher wrote a
 * different date in the text than the one Moodle enforces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuticTaskDetailSheet(
    sheetState: SheetState,
    task: DuticTask,
    detail: AssignmentDetail?,
    isLoading: Boolean,
    labels: DateTimeLabels,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = TienTheme.extendedColors

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
                .padding(bottom = TienTheme.spacing.loose)
        ) {
            SectionEyebrow(text = task.courseName)
            Spacer(Modifier.height(TienTheme.spacing.tight))

            Text(
                text = task.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(TienTheme.spacing.base))

            Row(
                horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.hidden) {
                    TienTag(
                        text = "Oculta",
                        color = extended.overdue,
                        containerColor = extended.overdueContainer,
                        contentDescription = "El calendario no muestra esta tarea"
                    )
                }
                task.dueDate?.let { due ->
                    MetaText(text = labels.dueLabel(due, isDone = !task.isPending))
                }
            }

            // The alert that justifies scraping the page at all.
            if (detail?.dateConflict == true) {
                Spacer(Modifier.height(TienTheme.spacing.base))
                ConflictNotice(detail, labels)
            }

            Spacer(Modifier.height(TienTheme.spacing.loose))

            when {
                isLoading -> {
                    LoadingState(modifier = Modifier.heightIn(min = 120.dp))
                }

                detail == null -> {
                    MetaText(text = "No se pudo abrir la consigna de esta tarea.")
                }

                else -> {
                    val description = detail.description
                    if (description.isNullOrBlank()) {
                        MetaText(text = "Esta tarea no tiene consigna escrita.")
                    } else {
                        SectionEyebrow(text = "Consigna")
                        Spacer(Modifier.height(TienTheme.spacing.snug))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            // Briefs run long; the sheet scrolls rather than
                            // truncating instructions the student has to follow.
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    if (detail.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(TienTheme.spacing.loose))
                        SectionEyebrow(
                            text = if (detail.attachments.size == 1) {
                                "1 archivo adjunto"
                            } else {
                                "${detail.attachments.size} archivos adjuntos"
                            }
                        )
                        Spacer(Modifier.height(TienTheme.spacing.snug))
                        detail.attachments.forEach { attachment ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    TienTheme.spacing.snug
                                ),
                                modifier = Modifier.padding(bottom = TienTheme.spacing.tight)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.height(18.dp)
                                )
                                Text(
                                    text = attachment.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    detail.timeRemaining?.let { remaining ->
                        Spacer(Modifier.height(TienTheme.spacing.loose))
                        SectionEyebrow(text = "Tiempo restante")
                        MetaText(text = remaining)
                    }
                }
            }
        }
    }
}

/**
 * The teacher wrote one deadline in the brief and configured another in Moodle.
 *
 * Shown with both dates rather than a vague warning: the student has to decide
 * which one to trust, and cannot do that without seeing them.
 */
@Composable
private fun ConflictNotice(detail: AssignmentDetail, labels: DateTimeLabels) {
    val extended = TienTheme.extendedColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TienTheme.spacing.base)
    ) {
        TienTag(
            text = "Fechas que no coinciden",
            color = extended.overdue,
            containerColor = extended.overdueContainer,
            contentDescription = "La consigna menciona una fecha distinta a la oficial"
        )
        Spacer(Modifier.height(TienTheme.spacing.snug))

        detail.dueDate?.let { official ->
            MetaText(text = "Moodle cierra el ${labels.dateTime(official)}")
        }
        detail.datesInDescription
            .filter { it.epochSeconds != null }
            .forEach { mentioned ->
                MetaText(
                    text = "La consigna dice “${mentioned.text}”",
                    color = extended.overdue
                )
            }
    }
}
