package com.tien.dutic

import com.tien.dutic.auth.DuticAuthenticator
import com.tien.dutic.auth.LoginCapture
import com.tien.dutic.core.DuticResult
import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.DuticConfig
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.domain.model.CourseGrades
import com.tien.dutic.domain.model.CourseModule
import com.tien.dutic.domain.model.CourseSection
import com.tien.dutic.domain.model.DuticCourse
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.model.Participant
import com.tien.dutic.domain.model.PersonProfile
import com.tien.dutic.domain.model.ResourceFile
import com.tien.dutic.domain.repository.AssignmentDetail
import com.tien.dutic.domain.repository.CoursesRepository
import com.tien.dutic.domain.repository.GradesRepository
import com.tien.dutic.domain.repository.PeopleRepository
import com.tien.dutic.domain.repository.PersonMatch
import com.tien.dutic.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import org.jsoup.Jsoup

/**
 * The module's public surface — one method per CLI tool.
 *
 * ### Why a facade and not exposed repositories
 * Every method here returns a [DuticResult] and can never throw. The repositories
 * behind it throw freely, because parsing code reads better that way; this class
 * is the single seam where those exceptions become values, via
 * [DuticAuthenticator.withSession]. A caller therefore has exactly one thing to
 * handle, and it is impossible to reach the network without a session check.
 *
 * Method names track [com.tien.dutic.tools.DuticToolCatalog], so a tool in the
 * CLI and a call here are visibly the same operation.
 */
class DuticClient internal constructor(
    private val authenticator: DuticAuthenticator,
    private val moodleClient: MoodleClient,
    private val courses: CoursesRepository,
    private val tasks: TasksRepository,
    private val grades: GradesRepository,
    private val people: PeopleRepository
) {

    // ── Session ─────────────────────────────────────────────────────────────

    /** Emits the live session; null while signed out. */
    val session: Flow<DuticSession?> get() = authenticator.session

    val isSignedIn: Flow<Boolean> get() = authenticator.isSignedIn

    /** `dutic_session_status` */
    suspend fun sessionStatus(): SessionStatus {
        val current = authenticator.currentSession()
        return if (current == null) {
            SessionStatus(isSignedIn = false)
        } else {
            SessionStatus(
                isSignedIn = true,
                semester = current.semester,
                siteUrl = current.siteUrl,
                capturedAt = current.capturedAt
            )
        }
    }

    /** `dutic_whoami` — the display name Moodle greets the user with. */
    suspend fun whoAmI(): DuticResult<String> = authenticator.withSession { session ->
        // A student account has no web-service call for this, so the dashboard's
        // own greeting is the source of truth.
        val html = moodleClient.getHtml(session, DuticConfig.dashboardUrl(session.siteUrl))
        Jsoup.parse(html)
            .selectFirst(".usermenu .usertext, .userbutton .usertext, h1")
            ?.text()
            ?.trim()
            .orEmpty()
    }

    /** `dutic_refresh_session` — stores what the login WebView captured. */
    suspend fun completeLogin(capture: LoginCapture): DuticResult<DuticSession> =
        authenticator.completeLogin(capture)

    suspend fun signOut() = authenticator.signOut()

    // ── Courses ─────────────────────────────────────────────────────────────

    /** `dutic_list_courses` */
    suspend fun listCourses(): DuticResult<List<DuticCourse>> =
        authenticator.withSession { courses.listCourses(it) }

    /** `dutic_get_course_contents` */
    suspend fun courseContents(courseId: Long): DuticResult<List<CourseSection>> =
        authenticator.withSession { courses.courseContents(it, courseId) }

    /** `dutic_list_course_materials` */
    suspend fun courseMaterials(courseId: Long): DuticResult<List<CourseModule>> =
        authenticator.withSession { courses.courseMaterials(it, courseId) }

    /** `dutic_list_course_files` */
    suspend fun courseFiles(courseId: Long): DuticResult<List<ResourceFile>> =
        authenticator.withSession { courses.courseFiles(it, courseId) }

    // ── Tasks ───────────────────────────────────────────────────────────────

    /**
     * `dutic_list_tasks` — every assignment, hidden ones included.
     *
     * @param enrich fetch each assignment's page to learn whether it was handed
     *   in. Accurate but one request per assignment; leave it off for a quick
     *   first paint and run it again with it on.
     */
    suspend fun listTasks(enrich: Boolean = true): DuticResult<List<DuticTask>> =
        authenticator.withSession { tasks.allTasks(it, enrich) }

    /** The calendar's view only — one call, near-instant. */
    suspend fun upcomingTasks(): DuticResult<List<DuticTask>> =
        authenticator.withSession { tasks.upcomingTasks(it) }

    /** `dutic_get_course_tasks` */
    suspend fun courseTasks(courseId: Long): DuticResult<List<DuticTask>> =
        authenticator.withSession { tasks.courseTasks(it, courseId) }

    /** `dutic_get_assignment_detail` */
    suspend fun assignmentDetail(cmid: Long): DuticResult<AssignmentDetail> =
        authenticator.withSession { tasks.assignmentDetail(it, cmid) }

    // ── Grades ──────────────────────────────────────────────────────────────

    /** `dutic_get_grades` for one course. */
    suspend fun courseGrades(courseId: Long): DuticResult<CourseGrades> =
        authenticator.withSession { grades.courseGrades(it, courseId) }

    /** `dutic_get_grades` / `dutic_compare_grades` across every course. */
    suspend fun allGrades(): DuticResult<List<CourseGrades>> =
        authenticator.withSession { grades.allGrades(it) }

    // ── People ──────────────────────────────────────────────────────────────

    /** `dutic_list_participants` */
    suspend fun participants(courseId: Long): DuticResult<List<Participant>> =
        authenticator.withSession { people.participants(it, courseId) }

    /** `dutic_get_course_teachers` */
    suspend fun teachers(courseId: Long): DuticResult<List<Participant>> =
        authenticator.withSession { people.teachers(it, courseId) }

    /** `dutic_find_person` */
    suspend fun findPerson(query: String): DuticResult<List<PersonMatch>> =
        authenticator.withSession { people.findPerson(it, query) }

    /** `dutic_get_person_profile` */
    suspend fun personProfile(userId: Long, courseId: Long? = null): DuticResult<PersonProfile> =
        authenticator.withSession { people.profile(it, userId, courseId) }

    // ── Raw ─────────────────────────────────────────────────────────────────

    /**
     * `dutic_fetch_page` — an authenticated GET of any aula virtual page.
     *
     * The escape hatch: Moodle has corners this module does not model, and
     * fetching the page beats adding a half-modelled repository for each one.
     */
    suspend fun fetchPage(url: String): DuticResult<String> = authenticator.withSession {
        require(DuticConfig.isAulaUrl(url)) {
            "fetchPage sólo acepta URLs del aula virtual"
        }
        moodleClient.getHtml(it, url)
    }
}

/** Answer of `dutic_session_status`. */
data class SessionStatus(
    val isSignedIn: Boolean,
    val semester: String? = null,
    val siteUrl: String? = null,
    val capturedAt: Long? = null
) {
    /**
     * Whether the session is old enough to be worth re-checking. Informational
     * only — see [DuticSession.isUsable] for why age never forces a refresh.
     */
    fun isStale(nowMillis: Long): Boolean {
        val captured = capturedAt ?: return false
        return nowMillis - captured >= DuticSession.SOFT_TTL_MILLIS
    }
}
