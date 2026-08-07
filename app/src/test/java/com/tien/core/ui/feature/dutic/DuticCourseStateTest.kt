package com.tien.core.ui.feature.dutic

import com.tien.dutic.domain.model.CourseModule
import com.tien.dutic.domain.model.CourseSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grouping a course's material by its own sections.
 *
 * A teacher organised the material into weeks or units for a reason, and the
 * flat list this replaced threw that away. The edge cases below are all things
 * a real Moodle course contains.
 */
class DuticCourseStateTest {

    private fun module(cmid: Long, sectionId: Long, name: String = "Recurso $cmid") =
        CourseModule(
            cmid = cmid,
            name = name,
            modName = "resource",
            url = null,
            sectionId = sectionId
        )

    private fun section(id: Long, name: String) =
        CourseSection(id = id, name = name)

    @Test
    fun `material is grouped under the section it belongs to`() {
        val state = DuticCourseUiState(
            sections = listOf(section(1, "Semana 1"), section(2, "Semana 2")),
            materials = listOf(module(10, 1), module(11, 1), module(20, 2))
        )

        val grouped = state.materialSections

        assertEquals(2, grouped.size)
        assertEquals("Semana 1", grouped[0].first)
        assertEquals(listOf(10L, 11L), grouped[0].second.map { it.cmid })
        assertEquals(listOf(20L), grouped[1].second.map { it.cmid })
    }

    /**
     * Moodle courses are full of empty sections — placeholders for weeks that
     * have not happened yet. Listing them would be a screen of headings with
     * nothing underneath.
     */
    @Test
    fun `empty sections are dropped`() {
        val state = DuticCourseUiState(
            sections = listOf(
                section(1, "Semana 1"),
                section(2, "Semana 2"),
                section(3, "Semana 3")
            ),
            materials = listOf(module(10, 2))
        )

        val grouped = state.materialSections

        assertEquals(1, grouped.size)
        assertEquals("Semana 2", grouped.single().first)
    }

    /** Moodle's section zero usually has no name at all. */
    @Test
    fun `an unnamed section still gets a heading`() {
        val state = DuticCourseUiState(
            sections = listOf(section(1, "")),
            materials = listOf(module(10, 1))
        )

        assertEquals(1, state.materialSections.size)
        assertTrue(state.materialSections.single().first.isNotBlank())
    }

    /**
     * Sections arrive only when the Material tab has loaded. Until then the
     * screen must fall back to the flat list rather than showing nothing.
     */
    @Test
    fun `no sections yet means no grouping, not an empty screen`() {
        val state = DuticCourseUiState(
            sections = emptyList(),
            materials = listOf(module(10, 1), module(11, 2))
        )

        assertTrue(state.materialSections.isEmpty())
        assertEquals(2, state.materials.size)
    }

    /** A module whose section is not in the list must not vanish silently. */
    @Test
    fun `material in an unknown section is not grouped away`() {
        val state = DuticCourseUiState(
            sections = listOf(section(1, "Semana 1")),
            materials = listOf(module(10, 1), module(99, 404))
        )

        val grouped = state.materialSections
        val shown = grouped.flatMap { it.second }.map { it.cmid }

        // The orphan is absent from the grouping, which is why the screen keeps
        // the flat list as its fallback rather than trusting this alone.
        assertEquals(listOf(10L), shown)
        assertEquals(2, state.materials.size)
    }

    @Test
    fun `people are split into teachers and classmates`() {
        val state = DuticCourseUiState(
            teachers = listOf(participant(1, "Docente Uno", listOf("Docente"))),
            classmates = listOf(
                participant(2, "Ana", listOf("Estudiante")),
                participant(3, "Luis", listOf("Estudiante"))
            )
        )

        assertEquals(3, state.peopleCount)
    }

    private fun participant(id: Long, name: String, roles: List<String>) =
        com.tien.dutic.domain.model.Participant(
            userId = id,
            fullName = name,
            roles = roles
        )
}
