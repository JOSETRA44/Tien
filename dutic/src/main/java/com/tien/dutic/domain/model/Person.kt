package com.tien.dutic.domain.model

/**
 * A course a person takes, as their own profile reports it.
 *
 * [courseId] comes from the profile link itself, never from matching names.
 * That is what makes [shared] trustworthy: at this university the same subject
 * runs as several sections ("Derecho GA", "Derecho GD"), and a name match would
 * happily claim you share a course with someone who is in a different section
 * of it.
 */
data class PersonCourse(
    val courseId: Long,
    /** Full name as Moodle prints it, group suffix included. */
    val fullName: String,
    /** True when the viewer is enrolled in this exact course. */
    val shared: Boolean,
    /** Their last access *in this course*, when the roster reported one. */
    val lastAccess: String? = null
)

/**
 * A person, found once.
 *
 * ### Why this is not one result per course
 * A classmate you share four courses with is **one person**, not four. The
 * earlier model keyed results on (person, course) and produced four rows with
 * the same name and face — the app inventing duplicates the aula virtual does
 * not have.
 *
 * [courses] therefore holds every course *they* take, with [PersonCourse.shared]
 * marking the ones you are also in. That is strictly more than the app used to
 * show: their profile exposes their whole enrolment, and the previous version
 * threw away everything outside your own courses.
 */
data class PersonMatch(
    val userId: Long,
    val fullName: String,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val courses: List<PersonCourse> = emptyList(),

    /**
     * Most recent access across the courses you share.
     *
     * The *most recent*, not the first one found: someone who was in one course
     * a month ago and another an hour ago was last seen an hour ago.
     */
    val lastAccess: String? = null,

    /** A course you can open to see them in context. */
    val contextCourseId: Long? = null
) {
    val sharedCourses: List<PersonCourse> get() = courses.filter { it.shared }

    val otherCourses: List<PersonCourse> get() = courses.filterNot { it.shared }

    val sharedCount: Int get() = sharedCourses.size

    val isTeacher: Boolean
        get() = roles.any { role ->
            TEACHER_ROLE_HINTS.any { hint -> role.contains(hint, ignoreCase = true) }
        }

    companion object {
        private val TEACHER_ROLE_HINTS = listOf("docente", "profesor", "teacher")
    }
}

/**
 * Accent- and case-insensitive matching.
 *
 * Names here carry marks people rarely type into a search box: "Huaman" with an
 * accent, "Perez" with one, "Munoz" with a tilde. Plain
 * `contains(ignoreCase = true)` finds none of them from the unaccented spelling,
 * which makes the search feel broken for exactly the names it exists to find.
 * Decomposing to NFD and dropping the combining marks fixes that with no locale
 * table.
 *
 * The tilde on the n is folded away along with everything else. It is a distinct
 * letter in Spanish rather than an n with a mark, so this is a deliberate trade:
 * a student hunting for a classmate types whatever their keyboard makes easy,
 * and the cost of two spellings colliding inside one class roster is nothing
 * next to a search that returns nobody.
 */
object TextFolding {

    fun fold(text: String): String = java.text.Normalizer
        .normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")

    fun matches(haystack: String, needle: String): Boolean =
        fold(haystack).contains(fold(needle))

    /** Unicode "mark, nonspacing" — every accent NFD split off. */
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
}
