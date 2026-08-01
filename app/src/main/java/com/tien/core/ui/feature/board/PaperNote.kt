package com.tien.core.ui.feature.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tien.core.domain.model.BoardNote
import com.tien.core.ui.designsystem.theme.BoardPalette
import com.tien.core.ui.designsystem.theme.paperTone
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One piece of paper on the wall.
 *
 * Everything here exists to answer one question: does this feel like paper?
 *
 * **It hangs crooked.** The tilt comes from the database, not from a random
 * number generated at draw time. A paper that re-tilts itself on every redraw is
 * unmistakably digital.
 *
 * **It lifts before it moves.** Picking one up scales it slightly, straightens
 * it toward level — you square up a sheet when you take hold of it — and grows
 * its shadow. The shadow is what sells the height: without it the note just gets
 * bigger.
 *
 * **It settles when dropped.** The tilt overshoots and springs back, the way a
 * sheet rocks on its pin before coming to rest.
 *
 * **It has thickness.** A darker strip along the bottom edge and a pin at the
 * top turn a rounded rectangle into an object.
 *
 * ### Why the transforms live in lambdas
 * `offset { }` and `graphicsLayer { }` take lambdas here, never plain values.
 * The lambda form defers the read to the layout and draw phases, so a drag
 * invalidates only those — with the value form, every frame of every drag would
 * recompose this composable and everything inside it.
 */
@Composable
fun PaperNote(
    note: BoardNote,
    palette: BoardPalette,
    isSelected: Boolean,
    isLinkSource: Boolean,
    onTap: () -> Unit,
    onPickUp: () -> Unit,
    onDrop: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tone = paperTone(note.color)

    // Live drag delta, in board units. Held locally so the gesture never round
    // trips through the ViewModel and the database mid-drag.
    var dragOffset by remember(note.id) { mutableStateOf(Offset.Zero) }
    var isDragging by remember(note.id) { mutableStateOf(false) }

    // 0 = resting on the wall, 1 = fully in hand.
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "paperLift"
    )

    // Extra degrees added on release, springing back through zero so the sheet
    // rocks on its pin instead of stopping dead.
    val settleWobble = remember(note.id) { Animatable(0f) }

    // A new paper drops onto the wall rather than appearing.
    val appear = remember(note.id) { Animatable(0f) }
    LaunchedEffect(note.id) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = modifier
            // Layout phase only.
            .offset {
                IntOffset(
                    (note.x + dragOffset.x).roundToInt(),
                    (note.y + dragOffset.y).roundToInt()
                )
            }
            .size(note.width.dp, note.height.dp)
            // Draw phase only.
            .graphicsLayer {
                // Straightens toward level as it comes off the wall.
                rotationZ = note.rotation * (1f - LIFT_STRAIGHTEN * lift) + settleWobble.value

                val scale = (1f + LIFT_SCALE * lift) * (0.86f + 0.14f * appear.value)
                scaleX = scale
                scaleY = scale
                alpha = appear.value

                // Rotate and scale about the pin, not the centre: the sheet is
                // held at the top, so that is what it pivots around.
                transformOrigin = TransformOrigin(0.5f, 0.06f)

                shadowElevation = LIFT_SHADOW_DP * lift + RESTING_SHADOW_DP
                shape = PaperShape
                clip = false
                ambientShadowColor = palette.pinShadow
                spotShadowColor = palette.pinShadow
            }
            .pointerInput(note.id) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(note.id) {
                // After a long press, not immediately: a plain drag belongs to
                // the wall (panning), so a paper has to be deliberately taken
                // hold of first. The long press is also what makes the pick-up
                // haptic land at a moment the user caused.
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPickUp()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        // `amount` already arrives in board units: pointer input
                        // is delivered inside the camera's graphicsLayer, so
                        // Compose has undone the zoom for us.
                        dragOffset += amount
                    },
                    onDragEnd = {
                        val finalX = note.x + dragOffset.x
                        val finalY = note.y + dragOffset.y
                        isDragging = false
                        dragOffset = Offset.Zero
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDrop(finalX, finalY)

                        scope.launch {
                            // Kick the tilt, then let the spring bring it home.
                            settleWobble.snapTo(SETTLE_KICK_DEGREES)
                            settleWobble.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    }
                )
            }
            .drawBehind {
                drawPaperBody(tone.surface, tone.edge)
                if (isSelected || isLinkSource) {
                    drawSelectionRing(
                        color = if (isLinkSource) palette.thread else palette.selection
                    )
                }
            }
            .semantics {
                contentDescription = if (note.isBlank) {
                    "Papel vacío"
                } else {
                    "Papel: ${note.text.take(60)}"
                }
            }
    ) {
        Pin(palette = palette, modifier = Modifier.align(Alignment.TopCenter))

        Text(
            text = note.text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink,
            maxLines = MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    // Clears the pin.
                    top = 26.dp,
                    bottom = 14.dp
                )
        )
    }
}

/** The pin holding the sheet to the cork. */
@Composable
private fun Pin(palette: BoardPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 5.dp)
            .size(11.dp)
            .drawBehind {
                // Shadow first and slightly below, so the head reads as sitting
                // proud of the paper rather than printed on it.
                drawCircle(
                    color = palette.pinShadow,
                    radius = size.minDimension / 2f,
                    center = center + Offset(0.6f, 1.4f)
                )
                drawCircle(color = palette.pinHead, radius = size.minDimension / 2f)
                // Off-centre highlight: a single specular dot is what makes a
                // flat circle look domed.
                drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = size.minDimension / 6f,
                    center = center - Offset(size.minDimension / 6f, size.minDimension / 6f)
                )
            }
    )
}

/** Sheet body: flat face, darker bottom edge, subtly lighter top. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaperBody(
    surface: Color,
    edge: Color
) {
    val corner = 3.dp.toPx()

    // The edge strip peeking out below the face is the thickness of the sheet.
    drawRoundRect(
        color = edge,
        size = Size(size.width, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = surface,
        size = Size(size.width, size.height - EDGE_THICKNESS_PX),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
    )

    // Faint sheen down from the pin, as if lit from above.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            startY = 0f,
            endY = size.height * SHEEN_HEIGHT_RATIO
        ),
        size = Size(size.width, size.height * SHEEN_HEIGHT_RATIO)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionRing(color: Color) {
    val corner = 3.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
    )
}

private val PaperShape = RoundedCornerShape(3.dp)

/** How much of the tilt is removed while a paper is held. */
private const val LIFT_STRAIGHTEN = 0.65f

/** Growth while held. Enough to read as nearer, small enough to stay paper. */
private const val LIFT_SCALE = 0.07f

private const val RESTING_SHADOW_DP = 2f
private const val LIFT_SHADOW_DP = 16f

/** Degrees of overshoot on release. */
private const val SETTLE_KICK_DEGREES = -2.4f

/** Strip of darker tone below the face, read as the thickness of the sheet. */
private const val EDGE_THICKNESS_PX = 3f

/** How far down the sheet the top-lit sheen reaches. */
private const val SHEEN_HEIGHT_RATIO = 0.4f

/** Lines of text a sheet shows before eliding. */
private const val MAX_LINES = 8
