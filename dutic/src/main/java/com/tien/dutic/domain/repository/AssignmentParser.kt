package com.tien.dutic.domain.repository

import com.tien.dutic.domain.model.MentionedDate
import com.tien.dutic.domain.model.SubmissionStatus
import com.tien.dutic.domain.model.TaskAttachment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Everything an assignment page tells us.
 *
 * Mirrors `AssignDetail` in `src/domain/assign.ts`.
 */
data class AssignmentDetail(
    val submission: SubmissionStatus = SubmissionStatus.UNKNOWN,
    val grade: String? = null,
    val dueDate: Long? = null,
    val timeRemaining: String? = null,
    val description: String? = null,
    val attachments: List<TaskAttachment> = emptyList(),
    val datesInDescription: List<MentionedDate> = emptyList(),
    /**
     * True when the brief mentions a date more than a day from the official
     * close. The teacher wrote one deadline in the text and configured another —
     * the classic way a submission gets missed.
     */
    val dateConflict: Boolean = false
)

/**
 * Reads `mod/assign/view.php`.
 *
 * ### Why scrape at all
 * Moodle's calendar only emits *action* events: pending and future. An
 * assignment already submitted, or already overdue, produces no event — so the
 * only place its true state exists is the page itself. This parser is what turns
 * "there is an assignment module here" into "and you have not handed it in".
 *
 * A pure function over HTML, with no session or network, so every quirk below is
 * testable against a saved page.
 */
internal object AssignmentParser {

    fun parse(html: String, nowEpochSeconds: Long): AssignmentDetail {
        val document = Jsoup.parse(html)
        val fields = readSummaryTable(document)

        val submissionText = fields[FIELD_SUBMISSION_STATE].orEmpty()
        val gradingText = fields[FIELD_GRADING_STATE].orEmpty()

        val description = document.selectFirst(".activity-description")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }

        val closeDate = fields[FIELD_DUE_DATE]?.let(SpanishDates::parse)
        val mentioned = description?.let(SpanishDates::findAll).orEmpty()

        return AssignmentDetail(
            submission = classifySubmission(submissionText, gradingText),
            grade = fields[FIELD_GRADE]?.takeIf { it.isNotBlank() && it != EMPTY_CELL },
            dueDate = closeDate,
            timeRemaining = fields[FIELD_TIME_REMAINING]?.takeIf { it.isNotBlank() },
            description = description,
            attachments = readAttachments(document),
            datesInDescription = mentioned,
            dateConflict = hasDateConflict(closeDate, mentioned)
        )
    }

    /**
     * Moodle renders the assignment summary as a two-column table. Keys are
     * lower-cased so the lookup survives the sentence-case Moodle applies.
     */
    private fun readSummaryTable(document: Document): Map<String, String> =
        buildMap {
            document.select("table tr").forEach { row ->
                val cells = row.select("th, td")
                if (cells.size >= 2) {
                    val key = cells[0].text().normalise().lowercase()
                    val value = cells[1].text().normalise()
                    if (key.isNotBlank()) put(key, value)
                }
            }
        }

    private fun readAttachments(document: Document): List<TaskAttachment> =
        document.select(".activity-description a[href], .fileuploadsubmission a[href]")
            .mapNotNull { link ->
                val href = link.attr("abs:href").ifBlank { link.attr("href") }
                // Only real files: Moodle serves those through pluginfile.php,
                // and everything else in the brief is an ordinary hyperlink.
                if (!href.contains("pluginfile.php")) return@mapNotNull null
                val name = link.text().trim().ifBlank { href.substringAfterLast('/') }
                TaskAttachment(fileName = name, url = href)
            }
            .distinctBy { it.url }

    /**
     * Turns Moodle's two status strings into one state.
     *
     * Order matters: a graded assignment also reads as submitted, so grading is
     * checked first. "Sin calificar" has to be excluded explicitly — it contains
     * the word the graded check looks for.
     */
    internal fun classifySubmission(
        submissionState: String,
        gradingState: String
    ): SubmissionStatus {
        val submission = submissionState.lowercase()
        val grading = gradingState.lowercase()

        if (grading.contains("calificado") && !grading.contains("sin calificar")) {
            return SubmissionStatus.GRADED
        }
        if (NOT_SUBMITTED_PATTERN.containsMatchIn(submission)) {
            return SubmissionStatus.NOT_SUBMITTED
        }
        if (SUBMITTED_PATTERN.containsMatchIn(submission)) {
            return SubmissionStatus.SUBMITTED
        }
        return SubmissionStatus.UNKNOWN
    }

    private fun hasDateConflict(
        closeDate: Long?,
        mentioned: List<MentionedDate>
    ): Boolean {
        if (closeDate == null) return false
        return mentioned.any { date ->
            val epoch = date.epochSeconds ?: return@any false
            kotlin.math.abs(epoch - closeDate) > CONFLICT_THRESHOLD_SECONDS
        }
    }

    private fun String.normalise(): String = replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    private val NOT_SUBMITTED_PATTERN = Regex(
        "no se han realizado|no entregado|sin intento|nada entregado|todav[ií]a no"
    )
    private val SUBMITTED_PATTERN = Regex("enviado|entregado|para calificar")

    /** More than a day apart counts as the teacher contradicting Moodle. */
    private const val CONFLICT_THRESHOLD_SECONDS = 24L * 60 * 60

    private const val EMPTY_CELL = "-"

    // Field labels as this Spanish Moodle prints them.
    private const val FIELD_SUBMISSION_STATE = "estado de la entrega"
    private const val FIELD_GRADING_STATE = "estado de la calificación"
    private const val FIELD_DUE_DATE = "fecha de entrega"
    private const val FIELD_TIME_REMAINING = "tiempo restante"
    private const val FIELD_GRADE = "calificación"
}
