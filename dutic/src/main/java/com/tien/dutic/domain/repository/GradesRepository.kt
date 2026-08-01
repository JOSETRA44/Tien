package com.tien.dutic.domain.repository

import com.tien.dutic.core.DuticConfig
import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.core.TtlCache
import com.tien.dutic.domain.model.CourseGrades
import com.tien.dutic.domain.model.GradeItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Grades, scraped from `grade/report/user/index.php`.
 *
 * There is no AJAX equivalent on this install — the user grade report exists
 * only as a server-rendered page, so Jsoup is the whole mechanism. Mirrors
 * `src/domain/grades.ts`.
 */
internal class GradesRepository(
    private val client: MoodleClient,
    private val coursesRepository: CoursesRepository,
    private val cache: TtlCache
) {

    suspend fun courseGrades(
        session: DuticSession,
        courseId: Long,
        courseName: String = ""
    ): CourseGrades = cache.getOrPut(TtlCache.key("grades", session.siteUrl, courseId)) {
        val html = client.getHtml(session, DuticConfig.gradesUrl(session.siteUrl, courseId))
        GradeReportParser.parse(html, courseId, courseName)
    }

    /** Grades for every enrolled course. */
    suspend fun allGrades(session: DuticSession): List<CourseGrades> = coroutineScope {
        val courses = coursesRepository.listCourses(session)
        val gate = Semaphore(MAX_CONCURRENT)

        courses.map { course ->
            async {
                gate.withPermit {
                    runCatching { courseGrades(session, course.id, course.fullName) }
                        // A course whose report is closed to students should not
                        // blank the whole screen.
                        .getOrDefault(CourseGrades(course.id, course.fullName))
                }
            }
        }.map { it.await() }
    }

    private companion object {
        const val MAX_CONCURRENT = 3
    }
}

/**
 * Turns a Moodle user grade report into rows.
 *
 * Pure and network-free so its many quirks can be tested against a saved page.
 */
internal object GradeReportParser {

    fun parse(html: String, courseId: Long, courseName: String): CourseGrades {
        val document = Jsoup.parse(html)

        val rows = document.select("table.user-grade tr, table.generaltable tr")
            .mapNotNull { row -> parseRow(row) }

        val total = rows.lastOrNull { it.isTotal }

        return CourseGrades(
            courseId = courseId,
            courseName = courseName.ifBlank {
                document.selectFirst("h1, .page-header-headings h1")?.text()?.trim().orEmpty()
            },
            items = rows,
            total = total?.grade,
            totalPercentage = total?.percentage
        )
    }

    private fun parseRow(row: Element): GradeItem? {
        val cells = row.select("th, td")
        if (cells.size < MIN_CELLS) return null

        val labelCell = cells.first() ?: return null
        // The header row has no data cells of its own.
        if (labelCell.tagName() == "th" && row.select("td").isEmpty()) return null

        val type = labelCell.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
        val name = itemName(labelCell, type)
        if (name.isBlank()) return null

        val values = cells.drop(1).map { it.textOrNull() }

        return GradeItem(
            name = name,
            type = type,
            grade = parseGradeCell(cells.getOrNull(GRADE_COLUMN)),
            range = values.getOrNull(RANGE_COLUMN - 1),
            percentage = values.getOrNull(PERCENTAGE_COLUMN - 1),
            weight = values.getOrNull(WEIGHT_COLUMN - 1),
            isTotal = name.contains("total", ignoreCase = true)
        )
    }

    /**
     * The label cell glues the item type onto the name — "TareaTarea Individual
     * 01", because the icon's alt text sits inside it. The link text is the
     * clean name when there is one; otherwise the type prefix is stripped.
     */
    private fun itemName(cell: Element, type: String?): String {
        val linkText = cell.selectFirst("a")?.text()?.normalise().orEmpty()
        if (linkText.isNotBlank()) return linkText

        var name = cell.text().normalise()
        if (type != null && name.startsWith(type)) {
            name = name.removePrefix(type).trim()
        }
        return name
    }

    /**
     * Extracts the number, discarding the action menu Moodle appends to the
     * cell ("… Acciones Análisis de calificaciones") — text that would otherwise
     * be read as the grade.
     */
    private fun parseGradeCell(cell: Element?): String? {
        if (cell == null) return null
        val clone = cell.clone()
        clone.select(".action-menu, .dropdown, .menubar, script, .accesshide").remove()
        val text = clone.text().normalise()
        return NUMBER_PATTERN.find(text)?.value ?: text.takeIf { it.isMeaningful() }
    }

    private fun Element.textOrNull(): String? = text().normalise().takeIf { it.isMeaningful() }

    /** Moodle prints an unmarked cell as "-" or "(vacío)". */
    private fun String.isMeaningful(): Boolean =
        isNotBlank() && this != "-" && !EMPTY_PATTERN.matches(this)

    private fun String.normalise(): String = replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")
    private val NUMBER_PATTERN = Regex("""-?\d+(?:[.,]\d+)?""")
    private val EMPTY_PATTERN = Regex("""\(\s*vac[ií]o\s*\)""", RegexOption.IGNORE_CASE)

    private const val MIN_CELLS = 2

    // Column layout of Moodle's user grade report.
    private const val GRADE_COLUMN = 1
    private const val RANGE_COLUMN = 2
    private const val PERCENTAGE_COLUMN = 3
    private const val WEIGHT_COLUMN = 4
}
