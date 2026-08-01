package com.tien.core.ui.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * The app's one card surface.
 *
 * Separation comes from a hairline border and a tonal shift rather than from
 * elevation, which keeps a long list flat and quiet — shadows on every row read
 * as noise once there are more than a handful.
 *
 * [railColor] draws the urgency rail down the leading edge. It is the signature
 * element of the design: colour encodes how close a deadline is, so the agenda
 * can be triaged without reading a single date. Pass `null` for surfaces that
 * carry no deadline, such as notes.
 */
@Composable
fun TienCard(
    modifier: Modifier = Modifier,
    railColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val hairline = TienTheme.extendedColors.hairline

    // Animated so a task changing state (ticked off, deadline passing) reads as
    // a transition rather than a jump.
    val animatedRail by animateColorAsState(
        targetValue = railColor ?: Color.Transparent,
        animationSpec = tween(durationMillis = 280),
        label = "cardRail"
    )
    val railWidth by animateDpAsState(
        targetValue = if (railColor != null) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "cardRailWidth"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, hairline),
        tonalElevation = TienTheme.elevation.flat
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            if (railWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight()
                        // Rounded only on the leading edge so the rail follows
                        // the card's own corner instead of poking past it.
                        .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                        .background(animatedRail)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (railWidth > 0.dp) TienTheme.spacing.comfy else TienTheme.spacing.loose,
                        end = TienTheme.spacing.snug,
                        top = TienTheme.spacing.comfy,
                        bottom = TienTheme.spacing.comfy
                    ),
                content = content
            )
        }
    }
}

/** Hairline divider matching the card border, for grouping rows. */
@Composable
fun TienHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TienTheme.extendedColors.hairline)
    )
}

/** Border helper so non-card surfaces can match the card treatment. */
@Composable
fun Modifier.tienHairlineBorder(shape: androidx.compose.ui.graphics.Shape): Modifier =
    border(1.dp, TienTheme.extendedColors.hairline, shape)
