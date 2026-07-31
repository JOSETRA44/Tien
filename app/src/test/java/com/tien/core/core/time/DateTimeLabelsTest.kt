package com.tien.core.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * These tests are only possible because [TienClock] is injected. The previous
 * formatting helpers read `System.currentTimeMillis()` directly, so "is this
 * yesterday?" could not be asserted without changing the machine's clock.
 */
class DateTimeLabelsTest {

    private val zone = ZoneId.of("Europe/Madrid")

    /** Pinned at 2026-04-15 10:00 local time. */
    private val clock = FakeClock(
        now = LocalDateTime.of(2026, 4, 15, 10, 0).atZone(zone).toEpochSecond(),
        zone = zone
    )

    private val labels = DateTimeLabels(clock, Locale.forLanguageTag("es-ES"))

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toEpochSecond()

    @Test
    fun `under a minute reads as now`() {
        assertEquals("Ahora mismo", labels.relative(clock.nowEpochSeconds() - 30))
    }

    @Test
    fun `under an hour reads in minutes`() {
        assertEquals("Hace 25 min", labels.relative(clock.nowEpochSeconds() - 25 * 60))
    }

    @Test
    fun `same calendar day reads as today`() {
        assertEquals("Hoy, 02:30", labels.relative(at(2026, 4, 15, 2, 30)))
    }

    @Test
    fun `previous calendar day reads as yesterday`() {
        assertEquals("Ayer, 23:50", labels.relative(at(2026, 4, 14, 23, 50)))
    }

    /**
     * Under an hour, elapsed time wins over the calendar day: at 00:30, an edit
     * made 40 minutes earlier reads as recent rather than as "Ayer".
     */
    @Test
    fun `just across midnight still reads in minutes`() {
        val justAfterMidnight = FakeClock(at(2026, 4, 15, 0, 30), zone)
        val nightLabels = DateTimeLabels(justAfterMidnight, Locale.forLanguageTag("es-ES"))

        assertEquals("Hace 40 min", nightLabels.relative(at(2026, 4, 14, 23, 50)))
    }

    /** Past the one-hour mark the calendar day takes over. */
    @Test
    fun `hours across midnight reads as yesterday`() {
        val morning = FakeClock(at(2026, 4, 15, 3, 0), zone)
        val morningLabels = DateTimeLabels(morning, Locale.forLanguageTag("es-ES"))

        assertEquals("Ayer, 23:50", morningLabels.relative(at(2026, 4, 14, 23, 50)))
    }

    /** Clock skew must not produce "Hace -3 min". */
    @Test
    fun `future timestamps read as now`() {
        assertEquals("Ahora mismo", labels.relative(clock.nowEpochSeconds() + 600))
    }

    @Test
    fun `day labels use relative words for the near days`() {
        assertEquals("Hoy", labels.dayLabel(LocalDate.of(2026, 4, 15)))
        assertEquals("Mañana", labels.dayLabel(LocalDate.of(2026, 4, 16)))
        assertEquals("Ayer", labels.dayLabel(LocalDate.of(2026, 4, 14)))
    }

    @Test
    fun `past deadlines are marked as overdue`() {
        val label = labels.dueLabel(at(2026, 4, 14, 9, 0), isDone = false)
        assertEquals(true, label.startsWith("Vencida"))
    }

    @Test
    fun `completed tasks drop the overdue prefix`() {
        val label = labels.dueLabel(at(2026, 4, 14, 9, 0), isDone = true)
        assertEquals(false, label.startsWith("Vencida"))
    }

    /**
     * 2026-03-29 is the spring-forward date in Europe/Madrid: that day is 23
     * hours long. Adding 86 400 seconds would land inside the following day.
     */
    @Test
    fun `day range spans exactly one calendar day across a DST shift`() {
        val dstClock = FakeClock(at(2026, 3, 29, 12, 0), zone)
        val range = dstClock.dayRange(LocalDate.of(2026, 3, 29))

        assertEquals(23 * 3600L, range.endExclusive - range.startInclusive)
    }
}

/** Clock pinned to a fixed instant and zone. */
class FakeClock(
    private val now: Long,
    private val zone: ZoneId
) : TienClock {
    override fun nowEpochSeconds(): Long = now
    override fun zone(): ZoneId = zone
}
