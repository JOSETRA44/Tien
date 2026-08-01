package com.tien.dutic.domain.repository

import com.tien.dutic.core.DuticSession
import com.tien.dutic.core.MoodleClient
import com.tien.dutic.core.TtlCache
import com.tien.dutic.domain.model.CourseModule
import com.tien.dutic.domain.model.CourseSection
import com.tien.dutic.domain.model.CourseState
import com.tien.dutic.domain.model.DuticCourse
import com.tien.dutic.domain.model.ResourceFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Courses, their structure and their materials.
 *
 * Mirrors `src/domain/courses.ts` and `src/domain/resources.ts`.
 */
internal class CoursesRepository(
    private val client: MoodleClient,
    private val cache: TtlCache
) {

    /** Every course the student is enrolled in. */
    suspend fun listCourses(session: DuticSession): List<DuticCourse> =
        cache.getOrPut(TtlCache.key("courses", session.siteUrl)) {
            val data = client.post(
                session,
                "core_course_get_enrolled_courses_by_timeline_classification",
                JSONObject()
                    .put("offset", 0)
                    // limit 0 means "all" — a student with more than a page of
                    // courses would otherwise silently lose the rest.
                    .put("limit", 0)
                    .put("classification", "all")
                    .put("sort", "fullname")
            ) as? JSONObject

            val courses = data?.optJSONArray("courses") ?: JSONArray()
            (0 until courses.length()).mapNotNull { index ->
                courses.optJSONObject(index)?.toCourse()
            }
        }

    /**
     * The course's full published state.
     *
     * `core_courseformat_get_state` is used rather than
     * `core_course_get_contents` because the latter is blocked over AJAX on this
     * install — and because the state call returns *every* module, which is what
     * makes hidden assignments findable.
     */
    suspend fun courseState(session: DuticSession, courseId: Long): CourseState =
        cache.getOrPut(TtlCache.key("state", session.siteUrl, courseId)) {
            val raw = client.post(
                session,
                "core_courseformat_get_state",
                JSONObject().put("courseid", courseId)
            )

            // This endpoint is the odd one out: its `data` is a JSON *string*,
            // not an object, so it needs a second parse.
            val parsed = when (raw) {
                is String -> JSONObject(raw)
                is JSONObject -> raw
                else -> JSONObject()
            }

            parseCourseState(courseId, parsed)
        }

    /** Sections with their modules, ready to render. */
    suspend fun courseContents(session: DuticSession, courseId: Long): List<CourseSection> {
        val state = courseState(session, courseId)
        return state.sections.map { section ->
            section.copy(modules = state.modules.filter { it.sectionId == section.id })
        }
    }

    /** Only the modules that carry study material, flattened. */
    suspend fun courseMaterials(session: DuticSession, courseId: Long): List<CourseModule> =
        courseState(session, courseId).modules
            .filter { it.isMaterial && it.userVisible }

    /**
     * Downloadable files in a course.
     *
     * Derived from the material modules rather than from a file listing API:
     * this Moodle exposes no such call to students, and the module URL is what a
     * download would follow anyway.
     */
    suspend fun courseFiles(session: DuticSession, courseId: Long): List<ResourceFile> =
        courseMaterials(session, courseId)
            .filter { it.url != null }
            .map { module ->
                ResourceFile(
                    fileName = module.name,
                    fileUrl = requireNotNull(module.url),
                    moduleName = module.name,
                    modName = module.modName
                )
            }

    /** Drops cached course data so the next read hits the network. */
    suspend fun invalidate() = cache.invalidate()

    // ── Parsing ─────────────────────────────────────────────────────────────

    private fun JSONObject.toCourse(): DuticCourse {
        val contactsArray = optJSONArray("contacts") ?: JSONArray()
        val contacts = (0 until contactsArray.length()).mapNotNull { index ->
            contactsArray.optJSONObject(index)
                ?.optString("fullname")
                ?.takeIf { it.isNotBlank() }
        }
        return DuticCourse(
            id = optLong("id"),
            fullName = optString("fullname"),
            shortName = optString("shortname"),
            contacts = contacts
        )
    }

    private fun parseCourseState(courseId: Long, parsed: JSONObject): CourseState {
        val sectionArray = parsed.optJSONArray("section") ?: JSONArray()
        val sections = (0 until sectionArray.length()).mapNotNull { index ->
            sectionArray.optJSONObject(index)?.let { raw ->
                CourseSection(
                    id = raw.optLongLenient("id"),
                    name = raw.optString("title"),
                    // Moodle names this field "number" on some versions and
                    // "section" on others.
                    number = raw.optInt("number", raw.optInt("section", 0)),
                    visible = raw.optBoolean("visible", true)
                )
            }
        }

        val moduleArray = parsed.optJSONArray("cm") ?: JSONArray()
        val modules = (0 until moduleArray.length()).mapNotNull { index ->
            moduleArray.optJSONObject(index)?.let { raw ->
                CourseModule(
                    cmid = raw.optLongLenient("id"),
                    name = raw.optString("name"),
                    modName = raw.optString("module"),
                    url = raw.optString("url").takeIf { it.isNotBlank() },
                    visible = raw.optBoolean("visible", true),
                    userVisible = raw.optBoolean("uservisible", true),
                    sectionId = raw.optLongLenient("sectionid")
                )
            }
        }

        return CourseState(courseId = courseId, sections = sections, modules = modules)
    }
}

/**
 * Reads a numeric id that Moodle may serialise as either a number or a string.
 *
 * `core_courseformat_get_state` is inconsistent about this between versions, and
 * `optLong` returns 0 for a numeric string — which would silently orphan every
 * module from its section.
 */
internal fun JSONObject.optLongLenient(name: String, fallback: Long = 0L): Long {
    if (!has(name) || isNull(name)) return fallback
    return when (val value = opt(name)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: fallback
        else -> fallback
    }
}
