package com.tien.dutic.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading Moodle's participant table.
 *
 * Every case here is a shape that made the previous parser return an empty list,
 * which the UI then reported as "sin lista de participantes" — a course that
 * looked like it kept its roster private when it was simply being read wrong.
 */
class ParticipantsParserTest {

    private val courseId = 42L

    /** The real table: a checkbox column, then the name. */
    private fun participantsTable(rows: String) = """
        <html><body>
        <table id="participants">
          <thead><tr><th></th><th>Nombre</th><th>Roles</th><th>Grupos</th><th>Último acceso</th></tr></thead>
          <tbody>$rows</tbody>
        </table>
        </body></html>
    """.trimIndent()

    private fun row(userId: Long, name: String, role: String, lastAccess: String = "2 días") = """
        <tr>
          <td><input type="checkbox" name="user$userId"></td>
          <td><a href="https://aulavirtual.unsa.edu.pe/2026A/user/view.php?id=$userId&course=42">$name</a></td>
          <td>$role</td>
          <td>Sin grupos</td>
          <td>$lastAccess</td>
        </tr>
    """.trimIndent()

    /**
     * The bug this whole file exists for: the first cell is a checkbox, so
     * reading it finds no profile link and drops every single row.
     */
    @Test
    fun `reads the name from the second cell, not the checkbox`() {
        val html = participantsTable(
            row(101, "Ana Quispe Mamani", "Estudiante") +
                row(102, "Carlos Flores", "Docente")
        )

        val people = ParticipantsParser.parse(html, courseId)

        assertEquals(2, people.size)
        assertEquals("Ana Quispe Mamani", people[0].fullName)
        assertEquals(101L, people[0].userId)
    }

    @Test
    fun `reads roles from the third cell`() {
        val html = participantsTable(row(102, "Carlos Flores", "Docente"))

        val teacher = ParticipantsParser.parse(html, courseId).single()

        assertEquals(listOf("Docente"), teacher.roles)
        assertTrue("un Docente debe reconocerse como docente", teacher.isTeacher)
    }

    @Test
    fun `splits a person holding several roles`() {
        val html = participantsTable(row(103, "Rosa Huamán", "Docente, Estudiante"))

        val person = ParticipantsParser.parse(html, courseId).single()

        assertEquals(listOf("Docente", "Estudiante"), person.roles)
    }

    /**
     * Moodle pads the table out to `perpage` with blank rows. Requiring the
     * profile link is what separates a participant from filler.
     */
    @Test
    fun `discards the blank rows Moodle pads the table with`() {
        val html = participantsTable(
            row(101, "Ana Quispe", "Estudiante") +
                "<tr><td></td><td></td><td></td><td></td><td></td></tr>" +
                "<tr><td></td><td></td><td></td><td></td><td></td></tr>"
        )

        assertEquals(1, ParticipantsParser.parse(html, courseId).size)
    }

    /**
     * The dynamic-table web service returns a fragment whose table carries no
     * id, so the strict selector finds nothing and the parser must fall back to
     * every row. Without this the web-service path — the one that dodges the
     * group filter — would silently produce nothing.
     */
    @Test
    fun `parses the web service fragment, whose table has no id`() {
        val html = """
            <table class="generaltable">
              <tbody>${row(201, "Luis Pérez", "Estudiante")}</tbody>
            </table>
        """.trimIndent()

        val people = ParticipantsParser.parse(html, courseId)

        assertEquals(1, people.size)
        assertEquals("Luis Pérez", people.single().fullName)
    }

    /** Pages overlap when walking the paginated HTML. */
    @Test
    fun `the same person is not returned twice`() {
        val html = participantsTable(
            row(101, "Ana Quispe", "Estudiante") + row(101, "Ana Quispe", "Estudiante")
        )

        assertEquals(1, ParticipantsParser.parse(html, courseId).size)
    }

    @Test
    fun `a row without a profile link is not a participant`() {
        val html = participantsTable(
            "<tr><td></td><td>Texto suelto</td><td>Estudiante</td><td></td><td></td></tr>"
        )

        assertTrue(ParticipantsParser.parse(html, courseId).isEmpty())
    }

    @Test
    fun `empty html yields no participants rather than throwing`() {
        assertTrue(ParticipantsParser.parse("", courseId).isEmpty())
        assertTrue(ParticipantsParser.parse("<html><body></body></html>", courseId).isEmpty())
    }

    @Test
    fun `reads the declared total the page prints`() {
        val html = "<html><body><p>50 participantes encontrados</p></body></html>"

        assertEquals(50, ParticipantsParser.parseDeclaredTotal(html))
    }

    @Test
    fun `a page without a declared total returns null instead of guessing`() {
        assertNull(ParticipantsParser.parseDeclaredTotal("<html><body></body></html>"))
    }

    /** A dash is Moodle's "never", not a last-access value. */
    @Test
    fun `a dash in last access is treated as absent`() {
        val html = participantsTable(row(101, "Ana Quispe", "Estudiante", lastAccess = "-"))

        assertNull(ParticipantsParser.parse(html, courseId).single().lastAccess)
    }
}
