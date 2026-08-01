package com.tien.dutic.domain.repository

import com.tien.dutic.core.DuticConfig
import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.core.TtlCache
import com.tien.dutic.domain.model.Participant
import com.tien.dutic.domain.model.PersonProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Classmates and teachers.
 *
 * Mirrors `src/domain/people.ts`. Like grades, the participant list and profile
 * pages exist only as HTML for a student account, so this is scraping too.
 */
internal class PeopleRepository(
    private val client: MoodleClient,
    private val coursesRepository: CoursesRepository,
    private val cache: TtlCache
) {

    suspend fun participants(session: DuticSession, courseId: Long): List<Participant> =
        cache.getOrPut(TtlCache.key("participants", session.siteUrl, courseId)) {
            val html = client.getHtml(
                session,
                DuticConfig.participantsUrl(session.siteUrl, courseId)
            )
            ParticipantsParser.parse(html)
        }

    /** Just the people who teach the course. */
    suspend fun teachers(session: DuticSession, courseId: Long): List<Participant> =
        participants(session, courseId).filter { it.isTeacher }

    /**
     * Finds a person by name across every enrolled course.
     *
     * Searches all courses rather than asking for one, because a student
     * generally remembers the name and not which course it belongs to — which is
     * exactly why the CLI exposes this as its own tool.
     */
    suspend fun findPerson(session: DuticSession, query: String): List<PersonMatch> =
        coroutineScope {
            val needle = query.trim()
            if (needle.isBlank()) return@coroutineScope emptyList()

            val courses = coursesRepository.listCourses(session)
            val gate = Semaphore(MAX_CONCURRENT)

            courses.map { course ->
                async {
                    gate.withPermit {
                        runCatching {
                            participants(session, course.id)
                                .filter { it.fullName.contains(needle, ignoreCase = true) }
                                .map { PersonMatch(it, course.id, course.fullName) }
                        }.getOrDefault(emptyList())
                    }
                }
            }.flatMap { it.await() }
                // The same person teaches several courses; show them once.
                .distinctBy { it.participant.userId to it.courseId }
        }

    suspend fun profile(
        session: DuticSession,
        userId: Long,
        courseId: Long? = null
    ): PersonProfile = cache.getOrPut(TtlCache.key("profile", session.siteUrl, userId, courseId)) {
        val html = client.getHtml(
            session,
            DuticConfig.profileUrl(session.siteUrl, userId, courseId)
        )
        ProfileParser.parse(html, userId)
    }

    private companion object {
        /**
         * Searching every course means one participant page per course. Capped
         * so a student with twelve enrolments does not open twelve connections
         * to the university at once.
         */
        const val MAX_CONCURRENT = 3
    }
}

/** A person, plus where they were found. */
data class PersonMatch(
    val participant: Participant,
    val courseId: Long,
    val courseName: String
)

internal object ParticipantsParser {

    fun parse(html: String): List<Participant> {
        val document = Jsoup.parse(html)

        return document.select("table#participants tbody tr, table.generaltable tbody tr")
            .mapNotNull { row -> parseRow(row) }
    }

    private fun parseRow(row: Element): Participant? {
        val nameCell = row.selectFirst("th, td") ?: return null
        val link = nameCell.selectFirst("a[href*=/user/view.php], a[href*=/user/profile.php]")
            ?: return null

        val userId = USER_ID_PATTERN.find(link.attr("href"))
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null

        val fullName = link.text().trim().ifBlank { return null }
        val cells = row.select("td")

        return Participant(
            userId = userId,
            fullName = fullName,
            email = cells.firstOrNull { it.text().contains('@') }?.text()?.trim(),
            // Moodle renders roles as a comma-separated list inside an editable
            // widget; splitting on the comma is what separates them.
            roles = cells.getOrNull(ROLES_COLUMN)
                ?.text()
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            profileImageUrl = nameCell.selectFirst("img")?.attr("abs:src"),
            lastAccess = cells.lastOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private val USER_ID_PATTERN = Regex("""[?&]id=(\d+)""")
    private const val ROLES_COLUMN = 1
}

internal object ProfileParser {

    fun parse(html: String, userId: Long): PersonProfile {
        val document = Jsoup.parse(html)

        val fullName = document.selectFirst(".page-header-headings h1, h1")
            ?.text()?.trim().orEmpty()

        // The profile page groups facts into labelled cards; the label is what
        // identifies each value, since none of them carry stable ids.
        val fields = document.select(".profile_tree .node_category li, .contentnode dl")
            .associate { node ->
                val label = node.selectFirst("dt, .aabtn, strong")?.text()?.trim()?.lowercase()
                val value = node.selectFirst("dd, a, span")?.text()?.trim()
                (label ?: "") to (value ?: "")
            }

        return PersonProfile(
            userId = userId,
            fullName = fullName,
            email = fields.findValue("correo", "email"),
            country = fields.findValue("país", "pais", "country"),
            city = fields.findValue("ciudad", "city"),
            department = fields.findValue("departamento", "institución"),
            interests = fields.findValue("intereses")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            courses = document.select(".node_category a[href*=/course/view.php]")
                .map { it.text().trim() }
                .filter { it.isNotBlank() },
            profileImageUrl = document.selectFirst(".page-header-image img")?.attr("abs:src"),
            lastAccess = fields.findValue("último acceso", "ultimo acceso")
        )
    }

    private fun Map<String, String>.findValue(vararg labels: String): String? =
        entries.firstOrNull { entry ->
            labels.any { label -> entry.key.contains(label, ignoreCase = true) }
        }?.value?.takeIf { it.isNotBlank() }
}
