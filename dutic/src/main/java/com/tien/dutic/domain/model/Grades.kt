package com.tien.dutic.domain.model

/**
 * One row of a course's grade report.
 *
 * Values stay as **strings**, exactly as Moodle printed them ("16,00", "80,00 %",
 * "0–20"). Parsing them into numbers would mean guessing the decimal separator
 * and the grading scale, and would throw away the distinction between a zero and
 * an empty cell — which is the difference between failing and not yet marked.
 */
data class GradeItem(
    val name: String,
    /** Item type inferred from Moodle's row icon: Tarea, Asistencia… */
    val type: String? = null,
    /** The grade as shown, or null when it has not been marked yet. */
    val grade: String? = null,
    /** Range, e.g. "0–20". */
    val range: String? = null,
    val percentage: String? = null,
    val weight: String? = null,
    val isTotal: Boolean = false
) {
    val isGraded: Boolean get() = !grade.isNullOrBlank()
}

data class CourseGrades(
    val courseId: Long,
    val courseName: String,
    val items: List<GradeItem> = emptyList(),
    /** Course total, when Moodle computes one. */
    val total: String? = null,
    val totalPercentage: String? = null
) {
    val gradedCount: Int get() = items.count { it.isGraded && !it.isTotal }
    val pendingCount: Int get() = items.count { !it.isGraded && !it.isTotal }
}

/** A person enrolled in a course. */
data class Participant(
    val userId: Long,
    val fullName: String,
    val email: String? = null,
    /** Roles as Moodle labels them: Estudiante, Docente, Profesor no editor… */
    val roles: List<String> = emptyList(),
    val profileImageUrl: String? = null,
    val lastAccess: String? = null
) {
    /**
     * Whether this person teaches the course.
     *
     * Matched on the role label because this Moodle is Spanish-localised and
     * does not expose canonical role shortnames to a student's participant list.
     */
    val isTeacher: Boolean
        get() = roles.any { role ->
            TEACHER_ROLE_HINTS.any { hint -> role.contains(hint, ignoreCase = true) }
        }

    companion object {
        private val TEACHER_ROLE_HINTS = listOf("docente", "profesor", "teacher")
    }
}

/** A fuller profile, from the user's profile page. */
data class PersonProfile(
    val userId: Long,
    val fullName: String,
    val email: String? = null,
    val country: String? = null,
    val city: String? = null,
    val department: String? = null,
    val interests: List<String> = emptyList(),
    val courses: List<String> = emptyList(),
    val profileImageUrl: String? = null,
    val lastAccess: String? = null
)
