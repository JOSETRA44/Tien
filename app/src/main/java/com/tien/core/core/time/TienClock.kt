package com.tien.core.core.time

import com.tien.core.domain.repository.DayRange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Time source for the whole app.
 *
 * Injected rather than reached for statically so tests can pin "now" and a
 * time zone instead of depending on the machine's wall clock — the reason
 * relative-time formatting was previously untestable.
 */
interface TienClock {
    fun nowEpochSeconds(): Long
    fun zone(): ZoneId

    /**
     * Derived from [nowEpochSeconds], **not** from `LocalDate.now()`. Reading
     * the system clock here would let an implementation's "today" disagree with
     * its own "now" — which is exactly what it did, and what made every
     * calendar-boundary comparison untestable.
     */
    fun today(): LocalDate = toLocalDate(nowEpochSeconds())

    fun toLocalDateTime(epochSeconds: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone())

    fun toLocalDate(epochSeconds: Long): LocalDate = toLocalDateTime(epochSeconds).toLocalDate()

    /** Epoch seconds for the start of [date] in the current zone. */
    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone()).toEpochSecond()

    /**
     * The `[start, end)` epoch-second window covering [date].
     *
     * Computed through [ZonedDateTime] rather than by adding 86 400, so days
     * that are 23 or 25 hours long — DST transitions — still resolve to exactly
     * one calendar day.
     */
    fun dayRange(date: LocalDate): DayRange = DayRange(
        startInclusive = startOfDay(date),
        endExclusive = startOfDay(date.plusDays(1))
    )
}

/** Production clock: system time, device time zone. */
class SystemTienClock(
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault
) : TienClock {
    override fun nowEpochSeconds(): Long = Instant.now().epochSecond

    // Resolved per call: the user can change time zone while the app is running.
    override fun zone(): ZoneId = zoneProvider()
}
