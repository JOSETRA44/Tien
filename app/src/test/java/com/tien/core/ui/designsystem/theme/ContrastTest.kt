package com.tien.core.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast, as a test rather than a promise.
 *
 * Six of these pairs were failing WCAG AA when this file was written — including
 * `muted` at 2.5:1, the colour every eyebrow label in the app is painted with.
 * Nothing in a build catches that: the screens render, the text is visible to
 * anyone with good eyesight on a bright screen, and the defect ships.
 *
 * So the requirement lives here. Adjusting a palette value is now a change that
 * either keeps the app readable or fails the build.
 */
class ContrastTest {

    /**
     * WCAG 2.1 relative luminance. The 0.03928 branch and the 2.4 exponent are
     * the sRGB transfer function — a plain average of the channels would rate
     * yellow and blue as equally bright, which the eye does not.
     */
    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.relativeLuminance()
        val b = background.relativeLuminance()
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertReadable(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double = AA_NORMAL_TEXT
    ) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$label: %.2f:1, por debajo del mínimo %.1f:1".format(ratio, minimum),
            ratio >= minimum
        )
    }

    // ── Light scheme ────────────────────────────────────────────────────────

    @Test
    fun `muted text is readable on every light surface it lands on`() {
        val muted = LightExtendedColors.muted
        assertReadable("muted sobre Paper", muted, Paper)
        assertReadable("muted sobre surfaceContainerLow", muted, LIGHT_SURFACE_LOW)
        assertReadable("muted sobre surfaceVariant", muted, PaperSunk)
    }

    @Test
    fun `urgency colours are readable as text on the page`() {
        assertReadable("overdue", LightExtendedColors.overdue, Paper)
        assertReadable("today", LightExtendedColors.today, Paper)
        assertReadable("scheduled", LightExtendedColors.scheduled, Paper)
    }

    /**
     * Tags paint their colour on their own container, so the pair that matters
     * is not the one against the page — it is this one.
     */
    @Test
    fun `tag text is readable on its own container`() {
        val colors = LightExtendedColors
        assertReadable("tag overdue", colors.overdue, colors.overdueContainer)
        assertReadable("tag today", colors.today, colors.todayContainer)
        assertReadable("tag scheduled", colors.scheduled, colors.scheduledContainer)
    }

    // ── Dark scheme ─────────────────────────────────────────────────────────

    @Test
    fun `muted text is readable on the dark background`() {
        assertReadable("muted oscuro", DarkExtendedColors.muted, Graphite900)
    }

    @Test
    fun `urgency colours are readable in the dark scheme`() {
        assertReadable("overdue oscuro", DarkExtendedColors.overdue, Graphite900)
        assertReadable("today oscuro", DarkExtendedColors.today, Graphite900)
        assertReadable("scheduled oscuro", DarkExtendedColors.scheduled, Graphite900)
    }

    // ── Board ───────────────────────────────────────────────────────────────

    /**
     * Ink on paper is the only text on the board, and the paper stock is chosen
     * at random when a note is pinned — so every stock has to work, not just the
     * default one.
     */
    @Test
    fun `board ink is readable on every paper stock`() {
        val ink = LightBoardPaletteInk
        LightPaperSurfaces.forEach { (name, surface) ->
            assertReadable("tinta sobre $name", ink, surface)
        }
    }

    private companion object {
        /** WCAG AA for text below 18pt, or below 14pt bold. */
        const val AA_NORMAL_TEXT = 4.5

        val LIGHT_SURFACE_LOW = Color(0xFFF7F6F1)

        val LightBoardPaletteInk = Color(0xFF2B2A26)

        val LightPaperSurfaces = listOf(
            "cream" to Color(0xFFF6EFE2),
            "butter" to Color(0xFFF7E9B8),
            "mint" to Color(0xFFDCEBDD),
            "sky" to Color(0xFFDCE6F0),
            "blush" to Color(0xFFF2DEDC),
            "lilac" to Color(0xFFE4DEEE)
        )
    }
}
