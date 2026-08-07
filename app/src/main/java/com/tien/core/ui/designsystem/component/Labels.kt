package com.tien.core.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tien.core.domain.model.Priority
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * Structural section marker — "FIJADAS", "HOY", "ESTA SEMANA".
 *
 * Uppercasing happens here so every eyebrow in the app is consistent and
 * callers pass normal sentence-case strings.
 */
@Composable
fun SectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text.uppercase(),
        style = TienTextStyles.eyebrow,
        color = color,
        // An eyebrow is a label, and a label that wraps to three lines stops
        // being one. Uppercase plus 1.4 tracking makes these strings far wider
        // than they look in the source, so the guard is not theoretical: a long
        // display name in the aula virtual header already reaches it.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Compact tinted label for a small piece of metadata.
 *
 * [contentDescription] is set explicitly because the visible text is often an
 * abbreviation ("Alta") that reads poorly on its own in a screen reader.
 */
@Composable
fun TienTag(
    text: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = TienTextStyles.eyebrow,
            color = color,
            modifier = if (contentDescription != null) {
                Modifier.clearAndSetSemantics {
                    this.contentDescription = contentDescription
                }
            } else {
                Modifier
            }
        )
    }
}

/**
 * Priority shown as a dot plus a word.
 *
 * Colour alone would fail for the ~8% of men with a colour vision deficiency,
 * so the label always carries the meaning and the dot only reinforces it.
 */
@Composable
fun PriorityBadge(
    priority: Priority,
    modifier: Modifier = Modifier
) {
    val extended = TienTheme.extendedColors
    val (color, label) = when (priority) {
        Priority.HIGH -> extended.overdue to "Alta"
        Priority.MEDIUM -> extended.today to "Media"
        Priority.LOW -> extended.scheduled to "Baja"
    }

    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Prioridad $label"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = TienTextStyles.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** De-emphasised metadata line, e.g. a timestamp. */
@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        style = TienTextStyles.meta,
        color = color,
        // Metadata is secondary by definition; truncating it is the correct
        // recovery, and it keeps a long course name from pushing a card's
        // deadline off screen.
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

internal val TagPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
