package com.tien.core.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale.
 *
 * A named scale rather than literals scattered through the composables: the
 * previous screens mixed 4, 8, 10, 12, 14, 16 and 32dp with no system, so
 * nothing lined up across components and there was no single place to adjust
 * density.
 */
@Immutable
data class TienSpacing(
    val hair: Dp = 2.dp,
    val tight: Dp = 4.dp,
    val snug: Dp = 8.dp,
    val base: Dp = 12.dp,
    val comfy: Dp = 16.dp,
    val loose: Dp = 20.dp,
    val section: Dp = 28.dp,
    val page: Dp = 40.dp,

    /** Screen gutter. Every list and header aligns to this. */
    val gutter: Dp = 20.dp,

    /** Bottom padding that keeps the last row clear of the FAB. */
    val listBottom: Dp = 96.dp
)

val LocalTienSpacing = staticCompositionLocalOf { TienSpacing() }

/**
 * Elevation scale. Kept deliberately low: this design separates surfaces with
 * hairlines and tone rather than with shadow, so heavy elevation would fight
 * the flat, paper-like direction.
 */
@Immutable
data class TienElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 1.dp,
    val floating: Dp = 3.dp,
    val overlay: Dp = 6.dp
)

val LocalTienElevation = staticCompositionLocalOf { TienElevation() }
