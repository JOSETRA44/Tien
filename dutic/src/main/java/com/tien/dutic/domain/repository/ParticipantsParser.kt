package com.tien.dutic.domain.repository

import com.tien.dutic.domain.model.Participant
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Reads Moodle's participant table.
 *
 * ### The two mistakes this file exists to not make
 *
 * **The first cell is a checkbox, not the name.** Moodle's participant table
 * opens every row with a selection checkbox and puts the name — with the profile
 * link — in the *second* cell. Reading the first one finds no link, discards the
 * row, and produces an empty roster that looks exactly like a course which does
 * not share its participants.
 *
 * **The table is padded with blank rows.** Moodle fills the page out to
 * `perpage` with empty `<tr>`s. Requiring a profile link is what separates a
 * real participant from filler.
 */
internal object ParticipantsParser {

    fun parse(html: String, courseId: Long): List<Participant> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parse(html)

        // The dynamic-table web service returns a fragment whose <table> may
        // carry no id, so the strict selector finds nothing there. Falling back
        // to every row is safe: rows without a profile link are dropped below.
        val strict = document.select("table#participants tbody tr")
        val rows = if (strict.isNotEmpty()) strict else document.select("tr")

        return rows.mapNotNull { row -> parseRow(row, courseId) }
            // The same person can appear twice when pages overlap.
            .distinctBy { it.userId }
    }

    private fun parseRow(row: Element, courseId: Long): Participant? {
        val cells = row.select("th, td")
        if (cells.isEmpty()) return null

        // Second cell when there is one — the first is the checkbox column.
        val nameCell = if (cells.size > 1) cells[NAME_CELL] else cells[0]

        val link = nameCell.selectFirst("a[href*=user/view.php], a[href*=user/profile.php]")
            ?: return null

        val userId = USER_ID_PATTERN.find(link.attr("href"))
            ?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?: return null

        val fullName = link.text().normalise().ifBlank { nameCell.text().normalise() }
        if (fullName.isBlank()) return null

        return Participant(
            userId = userId,
            fullName = fullName,
            // The roster never carries the address — Moodle only exposes it on
            // the profile page, and only when site policy allows.
            email = null,
            roles = cells.textAt(ROLE_CELL)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            profileImageUrl = nameCell.selectFirst("img")?.attr("abs:src"),
            lastAccess = cells.textAt(LAST_ACCESS_CELL)
        )
    }

    /** Total the page declares: "50 participantes encontrados". */
    fun parseDeclaredTotal(html: String): Int? {
        val text = Jsoup.parse(html).body().text()
        return DECLARED_TOTAL_PATTERN.find(text)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun org.jsoup.select.Elements.textAt(index: Int): String? =
        getOrNull(index)?.text()?.normalise()?.takeIf { it.isNotBlank() && it != "-" }

    private fun String.normalise(): String = replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")
    private val USER_ID_PATTERN = Regex("""[?&]id=(\d+)""")
    private val DECLARED_TOTAL_PATTERN =
        Regex("""(\d+)\s+participantes?\s+encontrad""", RegexOption.IGNORE_CASE)

    // Column layout of Moodle's participant table.
    private const val NAME_CELL = 1
    private const val ROLE_CELL = 2
    private const val LAST_ACCESS_CELL = 4
}
