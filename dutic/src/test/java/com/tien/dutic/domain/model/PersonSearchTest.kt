package com.tien.dutic.domain.model

import com.tien.dutic.domain.repository.RelativeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search behaviours that were wrong.
 *
 * A person shared across four courses used to produce four identical rows, a
 * name typed without its accent found nobody, and "last seen" reported whichever
 * course happened to be scanned first.
 */
class PersonSearchTest {

    // ── Folding ─────────────────────────────────────────────────────────────

    @Test
    fun `an unaccented query finds an accented name`() {
        assertTrue(TextFolding.matches("Rosa Huamán Ticona", "huaman"))
        assertTrue(TextFolding.matches("Bruno Pérez", "perez"))
        assertTrue(TextFolding.matches("José Muñoz", "munoz"))
    }

    @Test
    fun `an accented query still finds the accented name`() {
        assertTrue(TextFolding.matches("Rosa Huamán", "Huamán"))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(TextFolding.matches("BRUNO QUISPE", "bruno"))
        assertTrue(TextFolding.matches("bruno quispe", "BRUNO"))
    }

    @Test
    fun `a name that does not contain the query is not a match`() {
        assertFalse(TextFolding.matches("Ana Quispe", "bruno"))
    }

    @Test
    fun `folding leaves plain text alone`() {
        assertEquals("bruno quispe", TextFolding.fold("Bruno Quispe"))
    }

    // ── One person, one result ──────────────────────────────────────────────

    private fun course(id: Long, name: String, shared: Boolean, access: String? = null) =
        PersonCourse(courseId = id, fullName = name, shared = shared, lastAccess = access)

    @Test
    fun `a match separates the courses shared with you from the rest`() {
        val match = PersonMatch(
            userId = 7,
            fullName = "Bruno Quispe",
            courses = listOf(
                course(1, "Cálculo II GA", shared = true),
                course(2, "Física II GA", shared = true),
                course(9, "Derecho GB", shared = false)
            )
        )

        assertEquals(2, match.sharedCount)
        assertEquals(listOf(1L, 2L), match.sharedCourses.map { it.courseId })
        assertEquals(listOf(9L), match.otherCourses.map { it.courseId })
    }

    /**
     * Sharing is decided by course **id**, never by name. The same subject runs
     * as several sections here, so a name comparison would claim you share a
     * course with someone who is in a different one.
     */
    @Test
    fun `a same-named course in another section is not shared`() {
        val myCourseIds = setOf(10L)
        val theirs = listOf(
            course(10, "Derecho GA", shared = false),
            course(11, "Derecho GD", shared = false)
        )

        val marked = theirs.map { it.copy(shared = it.courseId in myCourseIds) }

        assertTrue(marked.first { it.courseId == 10L }.shared)
        assertFalse(
            "mismo nombre, otra sección: no es compartido",
            marked.first { it.courseId == 11L }.shared
        )
    }

    @Test
    fun `a teacher is recognised from their role`() {
        val teacher = PersonMatch(userId = 1, fullName = "Ana", roles = listOf("Docente"))
        val student = PersonMatch(userId = 2, fullName = "Luis", roles = listOf("Estudiante"))

        assertTrue(teacher.isTeacher)
        assertFalse(student.isTeacher)
    }

    // ── Most recent access ──────────────────────────────────────────────────

    /**
     * Someone seen in one course a month ago and another an hour ago was last
     * seen an hour ago. Taking the first value found reports the wrong one.
     */
    @Test
    fun `a shorter span ranks as more recent`() {
        val hour = RelativeAccess.toSeconds("1 hora")
        val month = RelativeAccess.toSeconds("1 mes")

        assertTrue("una hora debe ser más reciente que un mes", hour < month)
    }

    @Test
    fun `concatenated units are added together`() {
        val combined = RelativeAccess.toSeconds("3 días 4 horas")
        val threeDays = RelativeAccess.toSeconds("3 días")

        assertTrue(combined > threeDays)
        assertEquals(3L * 24 * 3600 + 4 * 3600, combined)
    }

    /** "Nunca" must never win the most-recent contest. */
    @Test
    fun `never sorts last`() {
        assertEquals(Long.MAX_VALUE, RelativeAccess.toSeconds("Nunca"))
        assertTrue(RelativeAccess.toSeconds("1 año") < RelativeAccess.toSeconds("Nunca"))
    }

    /** An unreadable string must not be mistaken for "just now". */
    @Test
    fun `unparseable text sorts last rather than first`() {
        assertEquals(Long.MAX_VALUE, RelativeAccess.toSeconds("hace un ratito"))
        assertEquals(Long.MAX_VALUE, RelativeAccess.toSeconds(null))
        assertEquals(Long.MAX_VALUE, RelativeAccess.toSeconds(""))
    }

    @Test
    fun `the units are ordered as expected`() {
        val ordered = listOf("30 segundos", "5 minutos", "2 horas", "3 días", "2 semanas")
            .map { RelativeAccess.toSeconds(it) }

        assertEquals(ordered.sorted(), ordered)
    }
}
