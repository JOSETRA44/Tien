package com.tien.core.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════
//  Tien — Type scale
//
//  No custom typeface is bundled. Shipping one would mean either a downloadable
//  -fonts dependency (a network round trip before first paint) or a ~400 KB
//  binary in the APK, and neither earns its cost here.
//
//  The personality comes from the *scale* instead: display sizes are heavy and
//  optically tightened with negative tracking, body copy sits at a comfortable
//  reading measure, and a dedicated `eyebrow` style — uppercase, wide tracking,
//  small — does the structural labelling. That contrast between tight-and-heavy
//  and wide-and-small is the typographic signature.
// ═══════════════════════════════════════════════════════════════════════════

private val TrimBoth = LineHeightStyle(
    // Removes the font's built-in leading so declared line heights are the
    // heights actually rendered — otherwise vertical rhythm drifts.
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun tienStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimBoth
)

val TienTypography = Typography(
    // Screen titles. Heavy and tightened — at this size, default tracking reads
    // loose and unresolved.
    displaySmall = tienStyle(34, 40, FontWeight.ExtraBold, -1.0),
    headlineMedium = tienStyle(26, 32, FontWeight.Bold, -0.6),
    headlineSmall = tienStyle(22, 28, FontWeight.Bold, -0.4),

    // Card titles.
    titleLarge = tienStyle(20, 26, FontWeight.SemiBold, -0.2),
    titleMedium = tienStyle(16, 22, FontWeight.SemiBold, 0.0),
    titleSmall = tienStyle(14, 20, FontWeight.Medium, 0.1),

    // Reading copy. 15sp/22 is the compromise between fitting a useful preview
    // and staying comfortable for a full note body.
    bodyLarge = tienStyle(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = tienStyle(15, 22, FontWeight.Normal, 0.1),
    bodySmall = tienStyle(13, 18, FontWeight.Normal, 0.2),

    // Controls.
    labelLarge = tienStyle(14, 20, FontWeight.SemiBold, 0.2),
    labelMedium = tienStyle(12, 16, FontWeight.Medium, 0.4),

    // Structural labels — see `TienTextStyles.eyebrow`.
    labelSmall = tienStyle(11, 16, FontWeight.Bold, 1.4)
)

/** Styles with no Material slot to live in. */
object TienTextStyles {

    /**
     * Section markers ("FIJADAS", "HOY"). Uppercase with wide tracking, so it
     * reads as structure rather than as content. Always pair with
     * `text.uppercase()` at the call site — the style sets the spacing, the
     * caller supplies the case.
     */
    val eyebrow: TextStyle = tienStyle(11, 16, FontWeight.Bold, 1.4)

    /** Numerals in the agenda summary. */
    val metric: TextStyle = tienStyle(28, 32, FontWeight.ExtraBold, -0.8)

    /** Timestamps and other de-emphasised metadata. */
    val meta: TextStyle = tienStyle(12, 16, FontWeight.Medium, 0.2)
}
