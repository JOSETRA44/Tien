package com.tien.dutic.domain.repository

import com.tien.dutic.domain.model.SubmissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading an assignment page.
 *
 * This parser decides whether the app tells a student "you have not handed this
 * in". A misread here is worse than no feature at all, so the Spanish strings
 * Moodle actually prints are pinned down here.
 */
class AssignmentParserTest {

    private val now = 1_776_000_000L

    private fun pageWith(
        submissionState: String,
        gradingState: String,
        dueDate: String = "martes, 15 de abril de 2026, 23:59",
        grade: String = "-",
        description: String = ""
    ) = """
        <html><body>
          <div class="activity-description">$description</div>
          <table>
            <tr><th>Estado de la entrega</th><td>$submissionState</td></tr>
            <tr><th>Estado de la calificación</th><td>$gradingState</td></tr>
            <tr><th>Fecha de entrega</th><td>$dueDate</td></tr>
            <tr><th>Tiempo restante</th><td>3 días 4 horas</td></tr>
            <tr><th>Calificación</th><td>$grade</td></tr>
          </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `reads an unsubmitted assignment`() {
        val detail = AssignmentParser.parse(
            pageWith("No se han realizado entregas", "Sin calificar"),
            now
        )

        assertEquals(SubmissionStatus.NOT_SUBMITTED, detail.submission)
        assertEquals("3 días 4 horas", detail.timeRemaining)
    }

    @Test
    fun `reads a submitted assignment`() {
        val detail = AssignmentParser.parse(
            pageWith("Enviado para calificar", "Sin calificar"),
            now
        )

        assertEquals(SubmissionStatus.SUBMITTED, detail.submission)
    }

    /**
     * "Sin calificar" contains "calificar", and a naive contains-check would
     * read every unmarked assignment as graded — hiding exactly the work the
     * student still has to do.
     */
    @Test
    fun `sin calificar is not graded`() {
        assertEquals(
            SubmissionStatus.NOT_SUBMITTED,
            AssignmentParser.classifySubmission("No se han realizado entregas", "Sin calificar")
        )
    }

    @Test
    fun `graded wins over submitted`() {
        // A graded assignment is also submitted; the more specific state must win.
        assertEquals(
            SubmissionStatus.GRADED,
            AssignmentParser.classifySubmission("Enviado para calificar", "Calificado")
        )
    }

    @Test
    fun `an unrecognised state stays unknown rather than guessing`() {
        assertEquals(
            SubmissionStatus.UNKNOWN,
            AssignmentParser.classifySubmission("Algo inesperado", "Otra cosa")
        )
    }

    @Test
    fun `parses the official due date`() {
        val detail = AssignmentParser.parse(pageWith("No se han realizado entregas", "Sin calificar"), now)

        val due = requireNotNull(detail.dueDate)
        val date = java.time.Instant.ofEpochSecond(due)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()

        assertEquals(2026, date.year)
        assertEquals(4, date.monthValue)
        assertEquals(15, date.dayOfMonth)
    }

    /**
     * The feature that catches missed deadlines: the teacher wrote one date in
     * the brief and configured another in Moodle.
     */
    @Test
    fun `flags a date in the brief that contradicts the official one`() {
        val detail = AssignmentParser.parse(
            pageWith(
                submissionState = "No se han realizado entregas",
                gradingState = "Sin calificar",
                dueDate = "martes, 15 de abril de 2026, 23:59",
                description = "Entregar hasta el 20 de abril de 2026 impostergable."
            ),
            now
        )

        assertTrue("una fecha 5 días distinta debe marcarse", detail.dateConflict)
        assertTrue(detail.datesInDescription.isNotEmpty())
    }

    @Test
    fun `does not flag a brief that repeats the official date`() {
        val detail = AssignmentParser.parse(
            pageWith(
                submissionState = "No se han realizado entregas",
                gradingState = "Sin calificar",
                dueDate = "martes, 15 de abril de 2026, 23:59",
                description = "Recuerden: la entrega es el 15 de abril de 2026."
            ),
            now
        )

        assertFalse(detail.dateConflict)
    }

    @Test
    fun `collects only real file attachments`() {
        val html = """
            <html><body><div class="activity-description">
              <a href="https://aulavirtual.unsa.edu.pe/pluginfile.php/1/mod_assign/rubrica.pdf">Rúbrica</a>
              <a href="https://ejemplo.com/articulo">Un enlace cualquiera</a>
            </div><table></table></body></html>
        """.trimIndent()

        val detail = AssignmentParser.parse(html, now)

        assertEquals(1, detail.attachments.size)
        assertEquals("Rúbrica", detail.attachments.first().fileName)
    }

    @Test
    fun `an empty grade cell is not a grade`() {
        val detail = AssignmentParser.parse(
            pageWith("No se han realizado entregas", "Sin calificar", grade = "-"),
            now
        )

        assertEquals(null, detail.grade)
    }
}
