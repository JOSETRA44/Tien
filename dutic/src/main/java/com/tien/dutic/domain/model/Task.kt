package com.tien.dutic.domain.model

/** Whether the student has handed this in, as far as Moodle will admit. */
enum class SubmissionStatus {
    NOT_SUBMITTED,
    SUBMITTED,
    GRADED,
    UNKNOWN;

    companion object {
        fun fromMoodle(raw: String?): SubmissionStatus = when (raw?.lowercase()) {
            "submitted" -> SUBMITTED
            "new", "draft", "notsubmitted", "not-submitted" -> NOT_SUBMITTED
            "graded" -> GRADED
            else -> UNKNOWN
        }
    }
}

/** Where a task was found. */
enum class TaskSource {
    /** It appeared as a calendar event — what the official app shows. */
    CALENDAR,

    /** It was found by sweeping the course's modules. */
    COURSE_SCAN
}

/**
 * An assignment.
 *
 * ### Why [hidden] is the point of this whole module
 * Moodle's timeline only returns **actionable** events: future, and not yet
 * submitted. An assignment with no calendar date, or already past due, simply
 * vanishes from the student's view — which is how deadlines get missed. Sweeping
 * every course turns those up, and [hidden] marks the ones the calendar never
 * would have shown.
 */
data class DuticTask(
    val id: Long,
    val name: String,
    val courseId: Long,
    val courseName: String,
    /** Due date as epoch **seconds**, or null when the assignment has none. */
    val dueDate: Long?,
    val url: String?,
    val description: String? = null,
    val source: TaskSource,
    val hidden: Boolean,
    val submission: SubmissionStatus = SubmissionStatus.UNKNOWN,
    /** Grade exactly as Moodle prints it, e.g. "16,00 / 20,00". */
    val grade: String? = null,
    /** Moodle's own "Tiempo restante" text, when it offers one. */
    val timeRemaining: String? = null,
    val attachments: List<TaskAttachment> = emptyList(),
    /**
     * True when the assignment text mentions a date more than a day away from
     * the official close. A real hazard: the teacher wrote a different date in
     * the body than the one Moodle enforces.
     */
    val dateConflict: Boolean = false,
    val datesInDescription: List<MentionedDate> = emptyList(),
    /** Course module id — the identifier every other call wants. */
    val cmid: Long? = null
) {
    val isPending: Boolean
        get() = submission == SubmissionStatus.NOT_SUBMITTED ||
            submission == SubmissionStatus.UNKNOWN

    fun isOverdue(nowEpochSeconds: Long): Boolean =
        dueDate != null && dueDate < nowEpochSeconds && isPending

    /**
     * Sort key. Unsubmitted work comes first regardless of date — that ordering
     * is the entire reason this app exists, so it is encoded on the model rather
     * than left to each caller.
     */
    val urgencyRank: Int
        get() = when (submission) {
            SubmissionStatus.NOT_SUBMITTED -> 0
            SubmissionStatus.UNKNOWN -> 1
            SubmissionStatus.SUBMITTED -> 2
            SubmissionStatus.GRADED -> 3
        }

    companion object {
        /**
         * Orders by urgency, then by due date, with undated work last inside its
         * group.
         */
        val ByUrgency: Comparator<DuticTask> = Comparator { a, b ->
            when {
                a.urgencyRank != b.urgencyRank -> a.urgencyRank - b.urgencyRank
                a.dueDate == null && b.dueDate == null -> 0
                a.dueDate == null -> 1
                b.dueDate == null -> -1
                else -> a.dueDate.compareTo(b.dueDate)
            }
        }
    }
}

/** A file attached to the assignment brief — a guide, a rubric. */
data class TaskAttachment(
    val fileName: String,
    val url: String
)

/** A date found inside the assignment text, with its parsed value if readable. */
data class MentionedDate(
    val text: String,
    val epochSeconds: Long?
)
