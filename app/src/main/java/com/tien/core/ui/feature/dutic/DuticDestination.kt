package com.tien.core.ui.feature.dutic

/**
 * Routes inside the aula virtual section.
 *
 * ### Why a course hub instead of a tool menu
 * The module exposes nineteen operations. Listing them as nineteen buttons would
 * be a command palette wearing a UI, and it would ask the student to know which
 * tool answers their question before they can ask it.
 *
 * At a university everything hangs off a **course**: its assignments, its
 * grades, its material, its people. That is the student's own mental model, so
 * it is the navigation model too — one course screen reaches four tools without
 * naming any of them.
 *
 * Only two things genuinely do not belong to a single course, and both stay at
 * the top level: the total workload, and finding a person (which searches every
 * course at once, so it cannot live inside one).
 */
internal object DuticRoute {

    /** The graph itself, so the bottom bar keeps "Aula" selected on any child. */
    const val GRAPH = "dutic"

    const val HOME = "dutic/home"

    const val COURSE = "dutic/course/{courseId}"
    fun course(courseId: Long) = "dutic/course/$courseId"

    const val PEOPLE = "dutic/people"

    const val PROFILE = "dutic/person/{userId}?courseId={courseId}"
    fun profile(userId: Long, courseId: Long?) =
        "dutic/person/$userId?courseId=${courseId ?: 0L}"

    const val ARG_COURSE_ID = "courseId"
    const val ARG_USER_ID = "userId"
}

/** Sections of the home screen. */
enum class DuticHomeTab {
    TASKS,
    COURSES,
    GRADES;

    val label: String
        get() = when (this) {
            TASKS -> "Tareas"
            COURSES -> "Cursos"
            GRADES -> "Notas"
        }
}

/**
 * Sections of a course.
 *
 * Ordered by how often a student needs them, not by how the API is shaped:
 * what must I hand in, where is the material, who do I ask, how am I doing.
 */
enum class DuticCourseTab {
    TASKS,
    MATERIAL,
    PEOPLE,
    GRADES;

    val label: String
        get() = when (this) {
            TASKS -> "Tareas"
            MATERIAL -> "Material"
            PEOPLE -> "Gente"
            GRADES -> "Notas"
        }
}
