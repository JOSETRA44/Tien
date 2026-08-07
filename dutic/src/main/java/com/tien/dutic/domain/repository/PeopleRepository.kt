package com.tien.dutic.domain.repository

import com.tien.dutic.core.DuticConfig
import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.core.RosterCache
import com.tien.dutic.core.TtlCache
import com.tien.dutic.domain.model.Participant
import com.tien.dutic.domain.model.PersonCourse
import com.tien.dutic.domain.model.PersonMatch
import com.tien.dutic.domain.model.PersonProfile
import com.tien.dutic.domain.model.TextFolding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Classmates and teachers.
 *
 * Mirrors `src/domain/people.ts`.
 */
internal class PeopleRepository(
    private val client: MoodleClient,
    private val coursesRepository: CoursesRepository,
    private val cache: TtlCache,
    private val rosterCache: RosterCache
) {

    /**
     * Everyone enrolled in the course.
     *
     * @param forceRefresh skips the stored roster and asks the server again.
     */
    suspend fun participants(
        session: DuticSession,
        courseId: Long,
        forceRefresh: Boolean = false
    ): List<Participant> {
        if (!forceRefresh) {
            rosterCache.read(session.siteUrl, courseId)?.let { return it }
        }

        val roster = cache.getOrPut(TtlCache.key("participants", session.siteUrl, courseId)) {
            fetchRoster(session, courseId)
        }

        if (roster.isNotEmpty()) {
            rosterCache.write(session.siteUrl, courseId, roster)
        }
        return roster
    }

    /**
     * Fetches the roster, web service first.
     *
     * ### Why not just read `/user/index.php`
     * That page can arrive with a **group filter already applied** — teachers
     * configure it on the course, and Moodle then renders only the viewer's own
     * group. On the web you have to press "Limpiar filtros" to see everyone.
     * Measured on a real course by the CLI: the page returned 4 participants
     * where there are 50, and the teacher was among the 46 it hid.
     *
     * The dynamic-table web service is the request that "Limpiar filtros" makes.
     * It takes one round trip and carries no inherited filter, so it is the
     * primary path; the paginated HTML is only the fallback for installs where
     * the service is unavailable.
     */
    private suspend fun fetchRoster(session: DuticSession, courseId: Long): List<Participant> {
        val viaService = runCatching { fetchViaDynamicTable(session, courseId) }
            .getOrDefault(emptyList())
        if (viaService.isNotEmpty()) return viaService

        return fetchViaPaginatedHtml(session, courseId)
    }

    /**
     * Calls `core_table_get_dynamic_table_content`.
     *
     * **The argument shape is not the obvious one.** `filters` is an object
     * keyed by filter name — not an array — and `pagenumber`/`pagesize` are
     * lowercase *strings*. Any other variant is rejected with
     * `invalidparameter`, which is why this is spelled out rather than
     * generated.
     */
    private suspend fun fetchViaDynamicTable(
        session: DuticSession,
        courseId: Long
    ): List<Participant> {
        val courseFilter = JSONObject()
            .put("name", "courseid")
            .put("jointype", JOIN_ANY)
            .put("values", JSONArray().put(courseId))

        val args = JSONObject()
            .put("component", "core_user")
            .put("handler", "participants")
            .put("uniqueid", "user-index-participants-$courseId")
            .put(
                "sortdata",
                JSONArray().put(
                    JSONObject().put("sortby", "lastname").put("sortorder", SORT_ASCENDING)
                )
            )
            .put("jointype", JOIN_ALL)
            .put("filters", JSONObject().put("courseid", courseFilter))
            .put("firstinitial", "")
            .put("lastinitial", "")
            .put("pagenumber", "1")
            // One page big enough for any course, so pagination never applies.
            .put("pagesize", "1000")
            .put("hiddencolumns", JSONArray())
            .put("resetpreferences", false)

        val data = client.post(session, "core_table_get_dynamic_table_content", args) as? JSONObject
        val html = data?.optString("html").orEmpty()
        return ParticipantsParser.parse(html, courseId)
    }

    /**
     * Walks `/user/index.php?page=0,1,2…`.
     *
     * Subject to the group filter described above, so it can return a partial
     * roster — but a partial list is far better than the empty one a
     * single-page read produces on a course with more than a hundred people.
     */
    private suspend fun fetchViaPaginatedHtml(
        session: DuticSession,
        courseId: Long
    ): List<Participant> {
        val byUser = LinkedHashMap<Long, Participant>()
        var declaredTotal: Int? = null

        for (page in 0 until MAX_PAGES) {
            val url = "${DuticConfig.participantsUrl(session.siteUrl, courseId)}" +
                "&page=$page&perpage=$PER_PAGE"
            val html = runCatching { client.getHtml(session, url) }.getOrNull() ?: break

            if (page == 0) declaredTotal = ParticipantsParser.parseDeclaredTotal(html)

            val rows = ParticipantsParser.parse(html, courseId)
            val before = byUser.size
            rows.forEach { byUser.putIfAbsent(it.userId, it) }

            // Stop on a page that added nothing, a short page, or once the
            // page's own declared total has been reached.
            if (byUser.size == before || rows.size < PER_PAGE) break
            if (declaredTotal != null && byUser.size >= declaredTotal) break
        }

        return byUser.values.toList()
    }

    /** Just the people who teach the course. */
    suspend fun teachers(session: DuticSession, courseId: Long): List<Participant> =
        participants(session, courseId).filter { it.isTeacher }

    /**
     * Finds people by name across every enrolled course.
     *
     * ### One person, one result
     * A classmate you share four courses with is one person. The earlier version
     * keyed results on (person, course) and returned four rows with the same
     * name — duplicates the aula virtual does not have.
     *
     * So candidates are grouped by user id, and then each one's **profile** is
     * opened to learn the courses they actually take. Sharing is decided by
     * exact course id, never by name: the same subject runs as several sections
     * here, and matching "Derecho" to "Derecho" would claim you share a course
     * with someone in a different one.
     */
    suspend fun findPerson(session: DuticSession, query: String): List<PersonMatch> =
        coroutineScope {
            val needle = query.trim()
            if (needle.isBlank()) return@coroutineScope emptyList()

            val myCourses = coursesRepository.listCourses(session)
            val myCourseIds = myCourses.map { it.id }.toSet()
            val gate = Semaphore(MAX_CONCURRENT)

            // 1. Scan the rosters, remembering where each person was seen.
            val sightings = myCourses.map { course ->
                async {
                    gate.withPermit {
                        runCatching { participants(session, course.id) }
                            .getOrDefault(emptyList())
                            .map { course.id to it }
                    }
                }
            }.flatMap { it.await() }

            // 2. Collapse to one entry per person, keeping the last access
            //    recorded in each course they appear in.
            val candidates = LinkedHashMap<Long, Candidate>()
            sightings.forEach { (courseId, participant) ->
                val existing = candidates[participant.userId]
                if (existing == null) {
                    candidates[participant.userId] = Candidate(
                        participant = participant,
                        contextCourseId = courseId,
                        accessByCourse = linkedMapOf(courseId to participant.lastAccess)
                    )
                } else {
                    existing.accessByCourse[courseId] = participant.lastAccess
                }
            }

            val matching = candidates.values.filter {
                TextFolding.matches(it.participant.fullName, needle)
            }

            // 3. Open each profile for their real enrolment.
            matching.map { candidate ->
                async {
                    gate.withPermit { toMatch(session, candidate, myCourses, myCourseIds) }
                }
            }.map { it.await() }
                .sortedWith(
                    // Most shared courses first: someone you see in four classes
                    // is far more likely to be who you meant.
                    compareByDescending<PersonMatch> { it.sharedCount }
                        .thenBy { it.fullName }
                )
        }

    private suspend fun toMatch(
        session: DuticSession,
        candidate: Candidate,
        myCourses: List<com.tien.dutic.domain.model.DuticCourse>,
        myCourseIds: Set<Long>
    ): PersonMatch {
        val profile = runCatching {
            profile(session, candidate.participant.userId, candidate.contextCourseId)
        }.getOrNull()

        val courses = profile?.courses.orEmpty()
            .map { course ->
                course.copy(
                    shared = course.courseId in myCourseIds,
                    lastAccess = candidate.accessByCourse[course.courseId]
                )
            }
            .ifEmpty {
                // A profile that exposed no courses still leaves us the one
                // where we found them, which beats showing none at all.
                val fallback = myCourses.firstOrNull { it.id == candidate.contextCourseId }
                listOfNotNull(
                    fallback?.let {
                        PersonCourse(
                            courseId = it.id,
                            fullName = it.fullName,
                            shared = true,
                            lastAccess = candidate.accessByCourse[it.id]
                        )
                    }
                )
            }
            .sortedByDescending { it.shared }

        return PersonMatch(
            userId = candidate.participant.userId,
            fullName = profile?.fullName?.takeIf { it.isNotBlank() }
                ?: candidate.participant.fullName,
            email = profile?.email,
            roles = candidate.participant.roles,
            courses = courses,
            lastAccess = mostRecentAccess(candidate.accessByCourse.values),
            contextCourseId = candidate.contextCourseId
        )
    }

    /**
     * The most recent of several "last access" strings.
     *
     * Moodle prints them relative ("3 dias 2 horas"), so they are ranked by the
     * span they describe — smallest wins. Taking the first one found instead
     * would report someone as last seen a month ago when they were in another
     * shared course an hour ago.
     */
    private fun mostRecentAccess(values: Collection<String?>): String? =
        values.filterNotNull()
            .filter { it.isNotBlank() }
            .minByOrNull { RelativeAccess.toSeconds(it) }

    private data class Candidate(
        val participant: Participant,
        val contextCourseId: Long,
        val accessByCourse: LinkedHashMap<Long, String?>
    )

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

    /** Drops the stored roster for one course, or for all of them. */
    suspend fun forgetRosters(siteUrl: String, courseId: Long? = null) {
        rosterCache.clear(siteUrl, courseId)
    }

    private companion object {
        /**
         * Searching every course means one roster per course. Capped so a
         * student with twelve enrolments does not open twelve connections to the
         * university at once.
         */
        const val MAX_CONCURRENT = 3

        const val PER_PAGE = 100
        const val MAX_PAGES = 50

        /** Moodle's filter join types: 1 = ANY, 2 = ALL. */
        const val JOIN_ANY = 1
        const val JOIN_ALL = 2

        /** Moodle's sort order constant for ascending. */
        const val SORT_ASCENDING = 4
    }
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
            email = extractEmail(document) ?: fields.findValue("correo", "email"),
            country = fields.findValue("país", "pais", "country"),
            city = fields.findValue("ciudad", "city"),
            department = fields.findValue("departamento", "institución"),
            interests = fields.findValue("intereses")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            courses = extractCourses(document),
            profileImageUrl = document.selectFirst(".page-header-image img")?.attr("abs:src"),
            lastAccess = fields.findValue("último acceso", "ultimo acceso")
        )
    }

    /**
     * Every course the person takes, from the "Perfiles de curso" card.
     *
     * Each entry is a link `user/view.php?id=<userId>&course=<COURSE_ID>` whose
     * text is the full course name including its group. **The id comes from the
     * link**, which is what identifies the exact course and section — reading
     * the name instead would confuse a course of yours with a same-named one in
     * a different group.
     */
    private fun extractCourses(document: org.jsoup.nodes.Document): List<PersonCourse> {
        val node = document.select(".node_category, .contentnode")
            .firstOrNull { COURSE_PROFILES_HEADING.containsMatchIn(it.text()) }
        val scope = node ?: document.selectFirst(".userprofile") ?: return emptyList()

        return scope.select("a[href*=user/view.php]")
            .mapNotNull { link ->
                val courseId = COURSE_ID_PATTERN.find(link.attr("href"))
                    ?.groupValues?.getOrNull(1)
                    ?.toLongOrNull()
                    ?: return@mapNotNull null
                val name = link.text().trim()
                // Short labels are navigation chrome, not course names.
                if (name.length < MIN_COURSE_NAME_LENGTH) return@mapNotNull null

                PersonCourse(courseId = courseId, fullName = name, shared = false)
            }
            .distinctBy { it.courseId }
    }

    /**
     * Reads the address out of the `mailto:` link.
     *
     * Moodle percent-encodes it as an anti-spam measure — the href arrives as
     * something like `%79%67…@%75ns%61.pe`, so reading the link's text finds
     * nothing usable. It has to be decoded first.
     */
    private fun extractEmail(document: org.jsoup.nodes.Document): String? {
        val mailto = document.selectFirst("a[href^=mailto:]")?.attr("href")
        if (!mailto.isNullOrBlank()) {
            val raw = mailto.removePrefix("mailto:")
            val decoded = runCatching {
                java.net.URLDecoder.decode(raw, Charsets.UTF_8.name())
            }.getOrDefault(raw)
            EMAIL_PATTERN.find(decoded)?.let { return it.value }
        }

        val body = document.select(".userprofile, #region-main").text()
        return EMAIL_PATTERN.find(body)?.value
    }

    private fun Map<String, String>.findValue(vararg labels: String): String? =
        entries.firstOrNull { entry ->
            labels.any { label -> entry.key.contains(label, ignoreCase = true) }
        }?.value?.takeIf { it.isNotBlank() }

    private val EMAIL_PATTERN = Regex("""[\w.+-]+@[\w.-]+\.\w{2,}""")
    private val COURSE_ID_PATTERN = Regex("""[?&]course=(\d+)""")
    private val COURSE_PROFILES_HEADING =
        Regex("perfiles de curso|course profiles", RegexOption.IGNORE_CASE)

    private const val MIN_COURSE_NAME_LENGTH = 6
}
