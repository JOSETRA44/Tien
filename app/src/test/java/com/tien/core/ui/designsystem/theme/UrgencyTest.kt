package com.tien.core.ui.designsystem.theme

import com.tien.core.domain.model.Priority
import com.tien.core.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Urgency drives the colour of the rail on every task card, so a
 * misclassification is directly visible to the user — worth pinning down.
 */
class UrgencyTest {

    private val now = 1_776_000_000L
    private val endOfToday = now + 8 * 3600L

    private fun task(
        dueAt: Long,
        isDone: Boolean = false
    ) = Task(
        id = 1,
        title = "Entregar informe",
        details = "",
        dueAt = dueAt,
        createdAt = now,
        updatedAt = now,
        priority = Priority.MEDIUM,
        isDone = isDone
    )

    @Test
    fun `completed work is never urgent`() {
        // Overdue *and* done: DONE has to win, or finished work would keep
        // shouting in red.
        val done = task(dueAt = now - 10_000, isDone = true)
        assertEquals(Urgency.DONE, Urgency.of(done, now, endOfToday))
    }

    @Test
    fun `a passed deadline is overdue`() {
        assertEquals(Urgency.OVERDUE, Urgency.of(task(now - 1), now, endOfToday))
    }

    @Test
    fun `due later today is today`() {
        assertEquals(Urgency.TODAY, Urgency.of(task(now + 3600), now, endOfToday))
    }

    @Test
    fun `due tomorrow but within 24h is soon`() {
        // Past the end of today, still inside the 24-hour window.
        assertEquals(Urgency.SOON, Urgency.of(task(now + 20 * 3600), now, endOfToday))
    }

    @Test
    fun `further out is scheduled`() {
        assertEquals(Urgency.SCHEDULED, Urgency.of(task(now + 5 * 86_400), now, endOfToday))
    }

    @Test
    fun `the exact deadline instant is not yet overdue`() {
        // dueAt == now means the moment has arrived, not passed.
        assertEquals(Urgency.TODAY, Urgency.of(task(now), now, endOfToday))
    }
}
