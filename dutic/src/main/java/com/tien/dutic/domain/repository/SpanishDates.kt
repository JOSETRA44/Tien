package com.tien.dutic.domain.repository

import com.tien.dutic.domain.model.MentionedDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the dates a Spanish Moodle writes, and the ones teachers type by hand.
 *
 * Mirrors `src/core/dates.ts`. Two different jobs:
 *
 *  - [parse] handles Moodle's own rendering of a due date, which is rigid:
 *    "martes, 15 de abril de 2026, 23:59".
 *  - [findAll] hunts through free prose for anything date-shaped, because the
 *    dates that cause missed submissions are the ones a teacher wrote into the
 *    brief rather than configured.
 *
 * Both are lenient by design. A date that fails to parse becomes a null epoch
 * rather than an exception — the surrounding text is still worth surfacing to
 * the student even when the machine cannot pin it to a day.
 */
internal object SpanishDates {

    private val MONTHS = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    /** "15 de abril de 2026" with an optional "23:59". */
    private val LONG_FORM = Regex(
        """(\d{1,2})\s+de\s+([a-záéíóúñ]+)\s+de\s+(\d{4})(?:[,\s]+(\d{1,2}):(\d{2}))?""",
        RegexOption.IGNORE_CASE
    )

    /** "15/04/2026" or "15-04-26". */
    private val NUMERIC_FORM = Regex(
        """(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})(?:\s+(\d{1,2}):(\d{2}))?"""
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Epoch **seconds** for the first date in [text], or null. */
    fun parse(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return parseLongForm(text) ?: parseNumericForm(text)
    }

    /** Every date-shaped fragment in [text], each with its epoch when readable. */
    fun findAll(text: String): List<MentionedDate> {
        val found = mutableListOf<MentionedDate>()

        LONG_FORM.findAll(text).forEach { match ->
            found += MentionedDate(
                text = match.value.trim(),
                epochSeconds = epochFromLongForm(match)
            )
        }
        NUMERIC_FORM.findAll(text).forEach { match ->
            found += MentionedDate(
                text = match.value.trim(),
                epochSeconds = epochFromNumericForm(match)
            )
        }

        return found.distinctBy { it.text }
    }

    private fun parseLongForm(text: String): Long? =
        LONG_FORM.find(text)?.let(::epochFromLongForm)

    private fun parseNumericForm(text: String): Long? =
        NUMERIC_FORM.find(text)?.let(::epochFromNumericForm)

    private fun epochFromLongForm(match: MatchResult): Long? {
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = MONTHS[match.groupValues[2].lowercase()] ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues.getOrNull(4)?.toIntOrNull() ?: DEFAULT_HOUR
        val minute = match.groupValues.getOrNull(5)?.toIntOrNull() ?: DEFAULT_MINUTE
        return toEpoch(year, month, day, hour, minute)
    }

    private fun epochFromNumericForm(match: MatchResult): Long? {
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val rawYear = match.groupValues[3].toIntOrNull() ?: return null
        // A two-digit year is this century: the aula virtual has no 1900s data.
        val year = if (rawYear < 100) TWENTY_FIRST_CENTURY + rawYear else rawYear
        val hour = match.groupValues.getOrNull(4)?.toIntOrNull() ?: DEFAULT_HOUR
        val minute = match.groupValues.getOrNull(5)?.toIntOrNull() ?: DEFAULT_MINUTE
        return toEpoch(year, month, day, hour, minute)
    }

    private fun toEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long? =
        runCatching {
            LocalDateTime.of(LocalDate.of(year, month, day), java.time.LocalTime.of(hour, minute))
                .atZone(zone)
                .toEpochSecond()
        }.getOrNull()

    /**
     * A date with no time means end of day. Treating it as 00:00 would mark
     * everything due today as already overdue.
     */
    private const val DEFAULT_HOUR = 23
    private const val DEFAULT_MINUTE = 59

    private const val TWENTY_FIRST_CENTURY = 2000
}
