package com.tien.core.ui.feature.dutic

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tien.core.ui.designsystem.component.MetaText
import com.tien.core.ui.designsystem.component.TienTag
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.dutic.domain.model.CourseModule
import com.tien.dutic.domain.model.DuticCourse
import com.tien.dutic.domain.model.GradeItem
import com.tien.dutic.domain.model.Participant
import com.tien.dutic.domain.model.PersonMatch

/**
 * The quiet half of the aula virtual.
 *
 * The section's boldness is already spent: the urgency rail and the dashed
 * "hidden" outline live on the task card, and that is where the eye should go.
 * Courses, people, material and grades are reference material, so they are
 * deliberately plain — one surface, one hairline, no accent unless it carries
 * meaning.
 */

/**
 * Segmented control shared by the home and course screens.
 *
 * The options are a compile-time `enum.entries` list — the same object every
 * recomposition — so the stability warning does not describe a real cost here.
 * See compose_compiler_config.conf for why `List` is declared stable.
 */
@Suppress("ComposeUnstableCollections")
@Composable
fun <T> DuticSegmentedTabs(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Four tabs stop fitting once the system font size goes up, and a
            // fixed Row would clip the last one with no way to reach it.
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = TienTheme.spacing.gutter,
                vertical = TienTheme.spacing.snug
            ),
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

/** A row that opens something. The shared shape for every list below. */
@Composable
private fun DuticRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            TienTheme.extendedColors.hairline
        ),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TienTheme.spacing.comfy),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.base),
            content = content
        )
    }
}

@Composable
fun CourseRow(
    course: DuticCourse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DuticRow(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (course.contacts.isNotEmpty()) {
                Spacer(Modifier.height(TienTheme.spacing.tight))
                // The teacher's name is the fastest way a student recognises a
                // course, faster than the institutional code.
                MetaText(text = course.contacts.joinToString(" · "))
            }
        }
    }
}

@Composable
fun PersonRow(
    participant: Participant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val extended = TienTheme.extendedColors

    DuticRow(onClick = onClick, modifier = modifier) {
        // Initials rather than a photo: Moodle's avatars need a second
        // authenticated request each, and a list of thirty would be thirty.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (participant.isTeacher) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = participant.fullName.initials(),
                style = TienTextStyles.eyebrow,
                color = if (participant.isTeacher) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val caption = subtitle ?: participant.roles.joinToString(" · ")
            if (caption.isNotBlank()) {
                Spacer(Modifier.height(TienTheme.spacing.hair))
                MetaText(text = caption)
            }
        }

        if (participant.isTeacher) {
            TienTag(
                text = "Docente",
                color = extended.scheduled,
                containerColor = extended.scheduledContainer,
                contentDescription = "Docente del curso"
            )
        }
    }
}

/**
 * A person found by searching, with what they share with you.
 *
 * The subtitle answers the question the search asked: not "who is this" but
 * "where do I know them from". A count plus the first shared course names the
 * connection without turning the row into a list.
 */
@Composable
fun PersonMatchRow(
    match: PersonMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = TienTheme.extendedColors

    DuticRow(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (match.isTeacher) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = match.fullName.initials(),
                style = TienTextStyles.eyebrow,
                color = if (match.isTeacher) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TienTheme.spacing.hair))
            MetaText(text = match.connectionSummary())
            match.lastAccess?.let { access ->
                MetaText(text = "Visto hace $access")
            }
        }

        if (match.isTeacher) {
            TienTag(
                text = "Docente",
                color = extended.scheduled,
                containerColor = extended.scheduledContainer,
                contentDescription = "Docente"
            )
        }
    }
}

/** "Contigo en 3 cursos - Calculo II" — the connection, then an example. */
private fun PersonMatch.connectionSummary(): String {
    val shared = sharedCourses
    return when {
        shared.isEmpty() -> "Sin cursos en comun"
        shared.size == 1 -> shared.single().fullName
        else -> "Contigo en ${shared.size} cursos - ${shared.first().fullName}"
    }
}

@Composable
fun MaterialRow(
    module: CourseModule,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    DuticRow(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = module.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MetaText(text = module.kindLabel())
        }
    }
}

/**
 * One row of a grade report.
 *
 * The grade is printed exactly as Moodle printed it — "16,00", not 16.0 — and
 * an unmarked item says so in words rather than showing a dash. The difference
 * between "you scored zero" and "this has not been marked" is the whole point of
 * looking at this screen.
 */
@Composable
fun GradeRow(
    item: GradeItem,
    modifier: Modifier = Modifier
) {
    val extended = TienTheme.extendedColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TienTheme.spacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.base)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = if (item.isTotal) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val caption = listOfNotNull(item.type, item.range?.let { "de $it" })
                .joinToString(" · ")
            if (caption.isNotBlank()) {
                MetaText(text = caption)
            }
        }

        if (item.isGraded) {
            Text(
                text = item.grade.orEmpty(),
                style = if (item.isTotal) TienTextStyles.metric else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            MetaText(text = "Sin calificar", color = extended.muted)
        }
    }
}

/** The list inset every aula virtual screen uses, so they stay aligned. */
@Composable
internal fun listPadding(): PaddingValues = PaddingValues(
    start = TienTheme.spacing.gutter,
    end = TienTheme.spacing.gutter,
    top = TienTheme.spacing.base,
    bottom = TienTheme.spacing.listBottom
)

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun String.initials(): String = trim()
    .split(' ')
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "?" }

private fun CourseModule.icon(): ImageVector = when (modName) {
    "folder" -> Icons.Outlined.Folder
    "url" -> Icons.Outlined.Link
    "page", "book" -> Icons.Outlined.MenuBook
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** Moodle's module type, in the words a student would use. */
private fun CourseModule.kindLabel(): String = when (modName) {
    "resource" -> "Archivo"
    "folder" -> "Carpeta"
    "url" -> "Enlace"
    "page" -> "Página"
    "book" -> "Libro"
    else -> modName.replaceFirstChar { it.uppercaseChar() }
}
