package com.tien.dutic.domain.repository

import com.tien.dutic.core.DuticConfig
import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.domain.model.DuticCourse
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.model.SubmissionStatus
import com.tien.dutic.domain.model.TaskSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Assignments — including the ones the calendar hides.
 *
 * Mirrors `src/domain/tasks.ts`. This is the reason the whole module exists, so
 * it is worth stating the mechanism plainly:
 *
 * 1. The **timeline** (`core_calendar_get_action_events_by_timesort`) returns
 *    only *actionable* events: future, and not yet submitted. It is what the
 *    official app shows.
 * 2. Sweeping every course's state turns up **every** assignment module,
 *    including those with no calendar date and those already past due.
 * 3. Anything present in (2) but absent from (1) is [DuticTask.hidden] — the
 *    work that silently disappears from a student's view.
 */
internal class TasksRepository(
    private val client: MoodleClient,
    private val coursesRepository: CoursesRepository,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) {

    /**
     * The fast path: what the calendar is willing to show.
     *
     * Cheap — one call, no sweep — so it is what a pull-to-refresh runs first
     * while the full scan continues behind it.
     */
    suspend fun upcomingTasks(session: DuticSession, limit: Int = DEFAULT_LIMIT): List<DuticTask> {
        val data = client.post(
            session,
            "core_calendar_get_action_events_by_timesort",
            JSONObject()
                .put("timesortfrom", now())
                .put("limitnum", limit)
        ) as? JSONObject

        val events = data?.optJSONArray("events") ?: JSONArray()
        return (0 until events.length())
            .mapNotNull { events.optJSONObject(it) }
            // The timeline carries quizzes and forums too; only assignments are
            // in scope here.
            .filter { it.optString("modulename") == MODULE_ASSIGN }
            .map { it.toTimelineTask() }
    }

    /**
     * The full picture: every assignment in every course, with hidden ones
     * marked and submission state resolved.
     *
     * @param enrich when true, each assignment's page is fetched to learn
     *   whether it was handed in. That is one request per assignment, so it is
     *   the slow, deliberate refresh — not something to run on every screen open.
     */
    suspend fun allTasks(
        session: DuticSession,
        enrich: Boolean = true
    ): List<DuticTask> = coroutineScope {
        val courses = coursesRepository.listCourses(session)

        // Indexed by cmid: presence here is what "the calendar shows it" means.
        val timeline = upcomingTasks(session).associateBy { it.cmid ?: it.id }

        // Courses are swept concurrently but capped. Unbounded parallelism
        // against a university server on a phone connection is how you get
        // throttled, and the CLI caps this for the same reason.
        val gate = Semaphore(MAX_CONCURRENT_COURSES)

        val perCourse = courses.map { course ->
            async {
                gate.withPermit {
                    runCatching { assignmentsIn(session, course, timeline) }
                        // One unreachable course must not sink the whole sweep;
                        // a partial list is far more useful than an error.
                        .getOrDefault(emptyList())
                }
            }
        }.awaitAll()

        val discovered = perCourse.flatten()

        val resolved = if (enrich) {
            val enrichGate = Semaphore(MAX_CONCURRENT_DETAILS)
            discovered.map { task ->
                async {
                    enrichGate.withPermit {
                        runCatching { enrichWithDetail(session, task) }.getOrDefault(task)
                    }
                }
            }.awaitAll()
        } else {
            discovered
        }

        resolved.sortedWith(DuticTask.ByUrgency)
    }

    /** Assignments in one course, without the per-assignment page fetch. */
    suspend fun courseTasks(session: DuticSession, courseId: Long): List<DuticTask> {
        val courses = coursesRepository.listCourses(session)
        val course = courses.firstOrNull { it.id == courseId }
            ?: DuticCourse(id = courseId, fullName = "", shortName = "")
        val timeline = upcomingTasks(session).associateBy { it.cmid ?: it.id }
        return assignmentsIn(session, course, timeline).sortedWith(DuticTask.ByUrgency)
    }

    /** Everything one assignment's page reveals. */
    suspend fun assignmentDetail(session: DuticSession, cmid: Long): AssignmentDetail {
        val html = client.getHtml(session, DuticConfig.assignUrl(session.siteUrl, cmid))
        return AssignmentParser.parse(html, now())
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private suspend fun assignmentsIn(
        session: DuticSession,
        course: DuticCourse,
        timeline: Map<Long, DuticTask>
    ): List<DuticTask> {
        val state = coursesRepository.courseState(session, course.id)

        return state.modules
            .filter { it.isAssignment && it.userVisible }
            .map { module ->
                val fromTimeline = timeline[module.cmid]
                DuticTask(
                    id = module.cmid,
                    name = module.name,
                    courseId = course.id,
                    courseName = course.fullName,
                    dueDate = fromTimeline?.dueDate,
                    url = module.url,
                    source = TaskSource.COURSE_SCAN,
                    // Absent from the timeline means the student would never
                    // have seen it.
                    hidden = fromTimeline == null,
                    submission = SubmissionStatus.UNKNOWN,
                    cmid = module.cmid
                )
            }
    }

    private suspend fun enrichWithDetail(
        session: DuticSession,
        task: DuticTask
    ): DuticTask {
        val url = task.url ?: return task
        val html = client.getHtml(session, url)
        val detail = AssignmentParser.parse(html, now())

        return task.copy(
            // The page's own due date wins: the timeline only has one while the
            // assignment is still pending.
            dueDate = detail.dueDate ?: task.dueDate,
            description = detail.description,
            submission = detail.submission,
            grade = detail.grade,
            timeRemaining = detail.timeRemaining,
            attachments = detail.attachments,
            dateConflict = detail.dateConflict,
            datesInDescription = detail.datesInDescription
        )
    }

    private fun JSONObject.toTimelineTask(): DuticTask {
        val url = optString("url").takeIf { it.isNotBlank() }
            ?: optJSONObject("action")?.optString("url")?.takeIf { it.isNotBlank() }
        val course = optJSONObject("course")

        return DuticTask(
            id = optLong("id"),
            name = optString("name"),
            courseId = course?.optLong("id") ?: 0L,
            courseName = course?.optString("fullname").orEmpty(),
            dueDate = optLong("timesort").takeIf { it > 0 }
                ?: optLong("timestart").takeIf { it > 0 },
            url = url,
            source = TaskSource.CALENDAR,
            hidden = false,
            cmid = cmidFromUrl(url)
        )
    }

    /** Pulls the course module id out of `mod/assign/view.php?id=123`. */
    private fun cmidFromUrl(url: String?): Long? {
        if (url == null) return null
        return CMID_PATTERN.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private companion object {
        const val MODULE_ASSIGN = "assign"
        const val DEFAULT_LIMIT = 50

        /**
         * Caps on concurrent requests. Courses are cheap (one AJAX call each);
         * detail pages are full HTML, so they get a tighter cap.
         */
        const val MAX_CONCURRENT_COURSES = 4
        const val MAX_CONCURRENT_DETAILS = 3

        val CMID_PATTERN = Regex("""[?&]id=(\d+)""")
    }
}

/** Awaits every deferred, preserving order. */
private suspend fun <T> List<kotlinx.coroutines.Deferred<T>>.awaitAll(): List<T> = map { it.await() }
