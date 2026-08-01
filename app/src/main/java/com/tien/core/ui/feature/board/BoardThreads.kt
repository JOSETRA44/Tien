package com.tien.core.ui.feature.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tien.core.domain.model.BoardLink
import com.tien.core.domain.model.BoardNote
import com.tien.core.ui.designsystem.theme.BoardPalette
import kotlin.math.hypot

/**
 * The threads tying papers together.
 *
 * Drawn as one canvas beneath every note, so a thread always passes *behind*
 * the sheets it connects — string tied to the back of a pin, not painted over
 * the front of the paper.
 *
 * **The sag is the point.** A straight line between two notes reads as a graph
 * edge. A quadratic curve pulled downward reads as string with weight on it,
 * and that single detail does more for the physical illusion than the colour or
 * the thickness. The sag grows with the span, because a longer piece of string
 * hangs lower.
 */
// `List` and `Map` are declared stable for this module in
// compose_compiler_config.conf: both arrive from an @Immutable UI state that the
// ViewModel replaces wholesale rather than mutates. The generated Compose report
// confirms this composable is skippable, so the lint rule is the stricter of the
// two here.
@Suppress("ComposeUnstableCollections")
@Composable
fun BoardThreads(
    links: List<BoardLink>,
    notesById: Map<Long, BoardNote>,
    palette: BoardPalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        links.forEach { link ->
            val from = notesById[link.fromNoteId] ?: return@forEach
            val to = notesById[link.toNoteId] ?: return@forEach
            drawThread(
                // Anchored at the pin, not the middle of the sheet: that is
                // where a real string would be tied.
                start = Offset(from.centerX, from.y + PIN_ANCHOR_DP),
                end = Offset(to.centerX, to.y + PIN_ANCHOR_DP),
                color = palette.thread
            )
        }
    }
}

/**
 * Draws one hanging thread, plus a soft shadow of it cast on the wall just
 * below — string this close to a surface always throws one, and its absence is
 * what makes a curve look like a drawing rather than an object.
 */
private fun DrawScope.drawThread(start: Offset, end: Offset, color: Color) {
    val span = hypot(end.x - start.x, end.y - start.y)
    val sag = (span * SAG_RATIO).coerceIn(MIN_SAG, MAX_SAG)

    // Control point below the midpoint. The curve reaches roughly half the
    // control point's displacement, which is why sag is doubled here.
    val control = Offset(
        x = (start.x + end.x) / 2f,
        y = (start.y + end.y) / 2f + sag * 2f
    )

    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }

    val shadowPath = Path().apply {
        moveTo(start.x + SHADOW_DROP, start.y + SHADOW_DROP)
        quadraticTo(
            control.x + SHADOW_DROP,
            control.y + SHADOW_DROP,
            end.x + SHADOW_DROP,
            end.y + SHADOW_DROP
        )
    }

    drawPath(
        path = shadowPath,
        color = Color.Black.copy(alpha = 0.18f),
        style = Stroke(width = THREAD_WIDTH.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = THREAD_WIDTH.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * The thread being drawn right now, following the finger to its second paper.
 *
 * Dashed and un-sagged: it is not string yet, it is an intention.
 */
fun DrawScope.drawPendingThread(start: Offset, end: Offset, color: Color) {
    drawPath(
        path = Path().apply {
            moveTo(start.x, start.y)
            lineTo(end.x, end.y)
        },
        color = color.copy(alpha = 0.55f),
        style = Stroke(
            width = THREAD_WIDTH.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(DASH_ON, DASH_OFF)
            )
        )
    )
}

/** Vertical distance from a note's top edge to its pin. */
private const val PIN_ANCHOR_DP = 10f

/** Sag as a fraction of the span between the two pins. */
private const val SAG_RATIO = 0.16f
private const val MIN_SAG = 8f
private const val MAX_SAG = 70f

private const val THREAD_WIDTH = 2f
private const val SHADOW_DROP = 3f

// Dash pattern for the thread being drawn but not yet tied.
private const val DASH_ON = 12f
private const val DASH_OFF = 10f
