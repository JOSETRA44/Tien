package com.tien.core.ui.designsystem.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
//  Tien — Colour tokens
//
//  The palette is built on one idea: the app's two halves run at different
//  temperatures. Notes are an archive — cool, ink-like, recessive. The agenda
//  is about pressure — warm, signalling, insistent. Warm hues are therefore
//  *reserved* for urgency and never used for chrome, so a warm pixel on screen
//  always means "this is time-sensitive".
//
//  The previous palette was a generic indigo (#4F6AF5) — effectively the
//  Material baseline, and indistinguishable from any other app using it.
// ═══════════════════════════════════════════════════════════════════════════

// ── Brand: deep pine ────────────────────────────────────────────────────────
internal val Pine900 = Color(0xFF0B211D)
internal val Pine800 = Color(0xFF12332F)
internal val Pine700 = Color(0xFF17453C)
internal val Pine600 = Color(0xFF1F6F5C)
internal val Pine500 = Color(0xFF2A8A73)
internal val Pine300 = Color(0xFF7CC3AE)
internal val Pine200 = Color(0xFFA8D9C8)
internal val Pine100 = Color(0xFFCFE3DA)
internal val Pine050 = Color(0xFFE8F2EE)

// ── Neutrals: warm paper, cool ink ──────────────────────────────────────────
// Slightly green-shifted greys rather than pure neutrals, so the surfaces sit
// in the same family as the brand instead of reading as a separate system.
internal val Paper = Color(0xFFFBFAF7)
internal val PaperSunk = Color(0xFFF2F1EC)
internal val PaperEdge = Color(0xFFDFE2DC)
internal val Graphite900 = Color(0xFF141714)
internal val Graphite800 = Color(0xFF1D211E)
internal val Graphite700 = Color(0xFF272C29)
internal val Graphite500 = Color(0xFF4A514D)

// Dark-scheme `muted`. Was #6C7470: 3.76:1 on the dark background.
internal val Graphite400 = Color(0xFF7B847F)

// Graphite300 was #9AA29E, which is 2.5:1 on Paper — well under the 4.5:1 that
// WCAG AA requires for text this size, and it is used for eyebrow labels. Its
// hue and saturation are unchanged; only the lightness moved.
internal val Graphite300 = Color(0xFF68706C)

// ── Urgency: the warm half of the system ────────────────────────────────────
// Used only for deadlines and priority. Nothing else in the app is allowed to
// be warm, which is what makes these read as a signal.
internal val Clay600 = Color(0xFFA3402F) // overdue
internal val Clay400 = Color(0xFFD9705C)
internal val Clay100 = Color(0xFFFBE0DA)

// Was #B4690E: 4.05:1 on Paper and 3.62:1 on its own container. Both now clear
// 4.5:1, which matters because this is the "vence hoy" colour.
internal val Ochre600 = Color(0xFF9E5C0C) // due today
internal val Ochre400 = Color(0xFFE8A33D)
internal val Ochre100 = Color(0xFFFDEBD0)

// Was #4C7A2E: 4.26:1 against its own container.
internal val Moss600 = Color(0xFF49762C) // comfortably ahead
internal val Moss400 = Color(0xFF7FAE5C)
internal val Moss100 = Color(0xFFE3EFD8)

// ── Dark-scheme counterparts ────────────────────────────────────────────────
internal val ClayDark = Color(0xFFFFB4A2)
internal val OchreDark = Color(0xFFF5C87A)
internal val MossDark = Color(0xFFAFD394)
