package com.tien.core.ui.feature.dutic

import com.tien.core.ui.designsystem.theme.Urgency
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.model.SubmissionStatus
import com.tien.dutic.domain.model.TaskSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The number this whole feature exists to produce.
 *
 * "How much of my pending work does the calendar not show me?" is the claim on
 * the screen. If that arithmetic is wrong the app is lying to a student about
 * their deadlines, so it is pinned down here.
 */
class DuticSummaryTest {

    private val now = 1_776_000_000L
    private val todayEnd = now + 8 * 3600L

    private fun task(
        id: Long,
        hidden: Boolean = false,
        submission: SubmissionStatus = SubmissionStatus.NOT_SUBMITTED,
        dueAt: Long? = now + 86_400
    ) = DuticTask(
        id = id,
        name = "Tarea $id",
        courseId = 1,
        courseName = "Cálculo II",
        dueDate = dueAt,
        url = null,
        source = if (hidden) TaskSource.COURSE_SCAN else TaskSource.CALENDAR,
        hidden = hidden,
        submission = submission,
        cmid = id
    )

    @Test
    fun `hidden is what pending has and the calendar does not`() {
        val summary = DuticSummary(pending = 5, visibleInCalendar = 2)

        assertEquals(3, summary.hidden)
        assertTrue(summary.hasHidden)
    }

    @Test
    fun `nothing hidden when the calendar shows everything`() {
        val summary = DuticSummary(pending = 4, visibleInCalendar = 4)

        assertEquals(0, summary.hidden)
        assertFalse(summary.hasHidden)
    }

    /**
     * During the first pass only the calendar has been read, so
     * `visibleInCalendar` equals `pending`. It must never exceed it — a negative
     * hidden count would render as "el calendario no te muestra -2 de ellas".
     */
    @Test
    fun `hidden never goes negative`() {
        val summary = DuticSummary(pending = 1, visibleInCalendar = 4)

        assertEquals(0, summary.hidden)
    }

    @Test
    fun `an empty workload reads as clear`() {
        assertTrue(DuticSummary(pending = 0).isClear)
        assertFalse(DuticSummary(pending = 1).isClear)
    }

    // ── Filtering ───────────────────────────────────────────────────────────

    @Test
    fun `the pending filter drops submitted work`() {
        val state = DuticUiState(
            tasks = listOf(
                task(1),
                task(2, submission = SubmissionStatus.SUBMITTED),
                task(3, submission = SubmissionStatus.GRADED)
            ),
            filter = DuticFilter.PENDING
        )

        assertEquals(listOf(1L), state.visibleTasks.map { it.id })
    }

    @Test
    fun `the hidden filter shows only what the calendar omitted`() {
        val state = DuticUiState(
            tasks = listOf(task(1), task(2, hidden = true), task(3, hidden = true)),
            filter = DuticFilter.HIDDEN
        )

        assertEquals(listOf(2L, 3L), state.visibleTasks.map { it.id })
    }

    /**
     * A hidden assignment that is already handed in still belongs in the hidden
     * list: the point of that tab is "what was invisible", not "what is owed".
     */
    @Test
    fun `the hidden filter keeps submitted work`() {
        val state = DuticUiState(
            tasks = listOf(task(9, hidden = true, submission = SubmissionStatus.SUBMITTED)),
            filter = DuticFilter.HIDDEN
        )

        assertEquals(1, state.visibleTasks.size)
    }

    // ── Urgency mapping ─────────────────────────────────────────────────────

    @Test
    fun `submitted work stops being urgent`() {
        val done = task(1, submission = SubmissionStatus.SUBMITTED, dueAt = now - 10_000)

        assertEquals(Urgency.DONE, done.toUrgency(now, todayEnd))
    }

    @Test
    fun `a passed deadline still owed is overdue`() {
        val late = task(1, dueAt = now - 1)

        assertEquals(Urgency.OVERDUE, late.toUrgency(now, todayEnd))
    }

    @Test
    fun `due later today is today`() {
        assertEquals(Urgency.TODAY, task(1, dueAt = now + 3600).toUrgency(now, todayEnd))
    }

    /**
     * Assignments with no due date are the most common kind of hidden work.
     * They must land somewhere calm — flagging them as overdue would make the
     * hidden tab a wall of false alarms.
     */
    @Test
    fun `an undated assignment is scheduled, not overdue`() {
        assertEquals(Urgency.SCHEDULED, task(1, dueAt = null).toUrgency(now, todayEnd))
    }

    @Test
    fun `due within a day but past midnight is soon`() {
        assertEquals(Urgency.SOON, task(1, dueAt = now + 20 * 3600).toUrgency(now, todayEnd))
    }
}
