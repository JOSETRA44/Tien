package com.tien.core.ui.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.tien.core.domain.model.PaperColor

// ═══════════════════════════════════════════════════════════════════════════
//  Tien — Board palette
//
//  The board is the one place in the app that models a physical object, so it
//  gets its own colour rules.
//
//  Everywhere else, warm hues are reserved for urgency (see Color.kt). Here the
//  *wall* is the warm element — cork is brown, and a wall does not have a
//  deadline — while the papers stay desaturated. Colour on a paper says which
//  sheet you grabbed, never how urgent it is, so the stocks below carry no
//  ranking and none of them borrows the Clay/Ochre urgency tones.
// ═══════════════════════════════════════════════════════════════════════════

@Immutable
data class BoardPalette(
    /** Base tone of the wall behind everything. */
    val wall: Color,

    /** Speckle colours that give the cork its grain. Two tones read as texture. */
    val wallGrainLight: Color,
    val wallGrainDark: Color,

    /** Vignette at the edges, so the wall reads as lit from the front. */
    val wallShadow: Color,

    /** The thread tying two papers together. */
    val thread: Color,

    /** Metal head of the pin holding a paper up. */
    val pinHead: Color,
    val pinShadow: Color,

    /** Ink written on the paper. */
    val ink: Color,
    val inkFaint: Color,

    /** Ring drawn around the selected paper. */
    val selection: Color
)

private val LightBoardPalette = BoardPalette(
    wall = Color(0xFFB08A5E),
    wallGrainLight = Color(0x33FFF3E0),
    wallGrainDark = Color(0x3A5B3F21),
    wallShadow = Color(0x40241505),
    thread = Color(0xFF9C3B2E),
    pinHead = Color(0xFFE8E4DC),
    pinShadow = Color(0x55201409),
    ink = Color(0xFF2B2A26),
    inkFaint = Color(0xFF6B6A62),
    selection = Pine600
)

private val DarkBoardPalette = BoardPalette(
    // A dark room, not a different wall: the same cork, dimmed.
    wall = Color(0xFF3B2E20),
    wallGrainLight = Color(0x22FFE9C8),
    wallGrainDark = Color(0x3A140C04),
    wallShadow = Color(0x66000000),
    thread = Color(0xFFC4584A),
    pinHead = Color(0xFFBFB9AE),
    pinShadow = Color(0x77000000),
    ink = Color(0xFF23221E),
    inkFaint = Color(0xFF5C5B54),
    selection = Pine300
)

/**
 * Paper stock: the sheet colour plus the slightly darker tone used for its
 * bottom edge, which is what makes a flat rectangle read as a sheet with
 * thickness rather than a coloured box.
 */
@Immutable
data class PaperTone(
    val surface: Color,
    val edge: Color
)

private val LightPapers = mapOf(
    PaperColor.CREAM to PaperTone(Color(0xFFF6EFE2), Color(0xFFE3D8C4)),
    PaperColor.BUTTER to PaperTone(Color(0xFFF7E9B8), Color(0xFFE4D095)),
    PaperColor.MINT to PaperTone(Color(0xFFDCEBDD), Color(0xFFC2D6C3)),
    PaperColor.SKY to PaperTone(Color(0xFFDCE6F0), Color(0xFFC1D0DF)),
    PaperColor.BLUSH to PaperTone(Color(0xFFF2DEDC), Color(0xFFDDC2BF)),
    PaperColor.LILAC to PaperTone(Color(0xFFE4DEEE), Color(0xFFCAC1DA))
)

// Dark mode dims the paper rather than inverting it. Paper does not glow, and a
// dark sheet with light text stops looking like paper at all.
private val DarkPapers = mapOf(
    PaperColor.CREAM to PaperTone(Color(0xFFCFC6B6), Color(0xFFAEA595)),
    PaperColor.BUTTER to PaperTone(Color(0xFFD3C48F), Color(0xFFB2A371)),
    PaperColor.MINT to PaperTone(Color(0xFFB6C7B7), Color(0xFF95A796)),
    PaperColor.SKY to PaperTone(Color(0xFFB5C2CE), Color(0xFF94A2AF)),
    PaperColor.BLUSH to PaperTone(Color(0xFFCCB8B6), Color(0xFFAB9795)),
    PaperColor.LILAC to PaperTone(Color(0xFFBDB6C8), Color(0xFF9C95A8))
)

/** Board palette for the active theme. */
val boardPalette: BoardPalette
    @Composable
    @ReadOnlyComposable
    get() = if (isDarkBoardTheme()) DarkBoardPalette else LightBoardPalette

/** Paper stock for [color] under the active theme. */
@Composable
@ReadOnlyComposable
fun paperTone(color: PaperColor): PaperTone {
    val papers = if (isDarkBoardTheme()) DarkPapers else LightPapers
    return papers[color] ?: papers.getValue(PaperColor.DEFAULT)
}

/** All stocks in enum order, for the paper picker. */
@Composable
@ReadOnlyComposable
fun paperTones(): List<PaperTone> {
    val papers = if (isDarkBoardTheme()) DarkPapers else LightPapers
    return PaperColor.entries.map { papers.getValue(it) }
}

/**
 * Derives dark mode from the resolved colour scheme rather than from
 * `isSystemInDarkTheme()`, so the board follows the user's explicit theme
 * choice — including "always light" while the device is dark.
 */
@Composable
@ReadOnlyComposable
private fun isDarkBoardTheme(): Boolean =
    androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < DARK_THRESHOLD

// Rec. 601 luma weights: the human eye is far more sensitive to green than to
// blue, so a plain average would call some light colours dark.
/** Below this luma the background counts as a dark scheme. */
private const val DARK_THRESHOLD = 0.5f

private const val LUMA_RED = 0.299f
private const val LUMA_GREEN = 0.587f
private const val LUMA_BLUE = 0.114f

private fun Color.luminance(): Float =
    LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue
