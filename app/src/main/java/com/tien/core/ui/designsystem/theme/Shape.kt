package com.tien.core.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner scale.
 *
 * Slightly softer than the Material default at the large end: cards are the
 * dominant surface in this app and a 16dp radius keeps a dense list from
 * reading as a stack of hard rectangles, while controls stay tighter so they
 * still feel pressable.
 */
val TienShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)
