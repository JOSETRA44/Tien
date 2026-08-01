package com.tien.core.core.time

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Human-readable date and time labels.
 *
 * Replaces the previous file-scope `SimpleDateFormat` values. Those were a
 * genuine hazard: `SimpleDateFormat` carries mutable parsing state, is
 * explicitly not thread-safe, and those instances were shared across every
 * composable recomposing on the main thread while the repository formatted day
 * keys on `Dispatchers.IO`. Concurrent use corrupts the internal calendar and
 * yields wrong dates rather than an exception, so it fails silently.
 *
 * `java.time` formatters are immutable and safe to share.
 *
 * `@Immutable` is a promise, and this class keeps it: every field is a `val`
 * holding an immutable value. It matters because the Compose compiler cannot
 * infer stability through `Locale` and `DateTimeFormatter` (Java types it knows
 * nothing about), so without the annotation this class is treated as unstable —
 * and it is a parameter of `NoteCard` and `TaskCard`, the composables inside the
 * scrolling lists where skipping matters most.
 */
@Immutable
class DateTimeLabels(
    private val clock: TienClock,
    private val locale: Locale = Locale.getDefault()
) {

    private val timeOnly: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", locale)

    private val dayAndMonth: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", locale)

    private val fullDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    private val fullDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale)

    private val weekdayDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM", locale)

    /** Absolute timestamp, e.g. "12 abr 2026, 14:30". */
    fun dateTime(epochSeconds: Long): String =
        fullDateTime.format(clock.toLocalDateTime(epochSeconds))

    /** Time of day only, e.g. "14:30". */
    fun time(epochSeconds: Long): String =
        timeOnly.format(clock.toLocalDateTime(epochSeconds))

    /**
     * Relative label for a note's last-edit time:
     * "Ahora mismo" · "Hace 5 min" · "Hoy, 14:30" · "Ayer, 09:00" · "12 abr" ·
     * "12 abr 2024".
     *
     * Two regimes, in this order:
     *  - Under an hour, elapsed time wins. Checking at 00:30 on something
     *    written at 23:50 gives "Hace 40 min", not "Ayer, 23:50" — for a recent
     *    edit, how long ago is more useful than which calendar day it fell on.
     *  - Beyond an hour, calendar days win, so "Hoy" and "Ayer" always mean the
     *    actual day rather than a rolling 24-hour window.
     */
    fun relative(epochSeconds: Long): String {
        val now = clock.nowEpochSeconds()
        val elapsed = Duration.ofSeconds(now - epochSeconds)

        // Future timestamps (clock skew, a restored note) read as "now" rather
        // than "hace -3 min".
        if (elapsed.isNegative || elapsed.toMinutes() < 1) return "Ahora mismo"
        if (elapsed.toMinutes() < MINUTES_PER_HOUR) return "Hace ${elapsed.toMinutes()} min"

        val date = clock.toLocalDate(epochSeconds)
        val today = clock.today()

        return when {
            date == today -> "Hoy, ${time(epochSeconds)}"
            date == today.minusDays(1) -> "Ayer, ${time(epochSeconds)}"
            date.year == today.year -> dayAndMonth.format(date)
            else -> fullDate.format(date)
        }
    }

    /**
     * Label for a day chip in the agenda: "Hoy" · "Mañana" · "Ayer" ·
     * "mié, 15 abr".
     */
    fun dayLabel(date: LocalDate): String {
        val today = clock.today()
        return when (date) {
            today -> "Hoy"
            today.plusDays(1) -> "Mañana"
            today.minusDays(1) -> "Ayer"
            else -> weekdayDate.format(date).replaceFirstChar { it.titlecase(locale) }
        }
    }

    /** Short weekday name for compact chips, e.g. "mié". */
    fun weekdayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            .replaceFirstChar { it.titlecase(locale) }

    private companion object {
        /** Boundary between the "minutes ago" and the calendar-day regimes. */
        const val MINUTES_PER_HOUR = 60L
    }

    /**
     * Deadline label for a task, prefixed with urgency:
     * "Vencida · 12 abr, 09:00" · "Vence hoy, 18:00" · "15 abr 2026, 10:00".
     */
    fun dueLabel(dueEpochSeconds: Long, isDone: Boolean): String {
        val date = clock.toLocalDate(dueEpochSeconds)
        val today = clock.today()
        val timeText = time(dueEpochSeconds)

        return when {
            isDone -> dateTime(dueEpochSeconds)
            dueEpochSeconds < clock.nowEpochSeconds() -> "Vencida · ${dateTime(dueEpochSeconds)}"
            date == today -> "Vence hoy, $timeText"
            date == today.plusDays(1) -> "Vence mañana, $timeText"
            date.year == today.year -> "${dayAndMonth.format(date)}, $timeText"
            else -> dateTime(dueEpochSeconds)
        }
    }
}
