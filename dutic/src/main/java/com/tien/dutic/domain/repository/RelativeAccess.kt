package com.tien.dutic.domain.repository

/**
 * Ranks Moodle's relative "last access" strings.
 *
 * Moodle prints them as prose — "3 dias 4 horas", "hace 2 minutos", "Nunca" —
 * and there is no absolute timestamp to sort on. This turns one into an
 * approximate age in seconds so several can be compared, which is what makes
 * "most recently seen across your shared courses" answerable.
 *
 * Approximate on purpose: the goal is ordering, not arithmetic. A string it
 * cannot read sorts last rather than first, so an unparsed value never wins the
 * "most recent" contest by accident.
 */
internal object RelativeAccess {

    private const val MINUTE = 60L
    private const val HOUR = 60L * MINUTE
    private const val DAY = 24L * HOUR
    private const val WEEK = 7L * DAY
    private const val MONTH = 30L * DAY
    private const val YEAR = 365L * DAY

    private val UNITS = listOf(
        Regex("""(\d+)\s*(?:a[nñ]os?|years?)""", RegexOption.IGNORE_CASE) to YEAR,
        Regex("""(\d+)\s*(?:meses|mes|months?|month)""", RegexOption.IGNORE_CASE) to MONTH,
        Regex("""(\d+)\s*(?:semanas?|weeks?)""", RegexOption.IGNORE_CASE) to WEEK,
        Regex("""(\d+)\s*(?:d[ií]as?|days?)""", RegexOption.IGNORE_CASE) to DAY,
        Regex("""(\d+)\s*(?:horas?|hours?)""", RegexOption.IGNORE_CASE) to HOUR,
        Regex("""(\d+)\s*(?:minutos?|mins?|minutes?)""", RegexOption.IGNORE_CASE) to MINUTE,
        Regex("""(\d+)\s*(?:segundos?|secs?|seconds?)""", RegexOption.IGNORE_CASE) to 1L
    )

    /** Age in seconds, or [Long.MAX_VALUE] when the text says nothing usable. */
    fun toSeconds(text: String?): Long {
        if (text.isNullOrBlank()) return Long.MAX_VALUE
        if (NEVER.containsMatchIn(text)) return Long.MAX_VALUE

        // Moodle concatenates units: "3 dias 4 horas" is both, added together.
        var total = 0L
        var matched = false
        UNITS.forEach { (pattern, seconds) ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { amount ->
                total += amount * seconds
                matched = true
            }
        }

        return if (matched) total else Long.MAX_VALUE
    }

    private val NEVER = Regex("nunca|never", RegexOption.IGNORE_CASE)
}
