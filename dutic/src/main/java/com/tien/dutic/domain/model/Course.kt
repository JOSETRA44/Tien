package com.tien.dutic.domain.model

/** A course the student is enrolled in. */
data class DuticCourse(
    val id: Long,
    val fullName: String,
    val shortName: String,
    /** Teacher/contact names, when Moodle exposes them on the course card. */
    val contacts: List<String> = emptyList()
)

/**
 * A module inside a course section: an assignment, a file, a folder, a quiz…
 *
 * [visible] and [userVisible] differ in a way that matters: a module can be
 * published yet still restricted from this student. Only [userVisible] means
 * "you can actually open this".
 */
data class CourseModule(
    val cmid: Long,
    val name: String,
    /** Moodle's canonical type name: assign, resource, folder, url, quiz, label… */
    val modName: String,
    val url: String?,
    val visible: Boolean = true,
    val userVisible: Boolean = true,
    val sectionId: Long = 0
) {
    val isAssignment: Boolean get() = modName == MOD_ASSIGN

    /** Types that carry downloadable study material. */
    val isMaterial: Boolean get() = modName in MATERIAL_MODULES

    companion object {
        const val MOD_ASSIGN = "assign"
        val MATERIAL_MODULES = setOf("resource", "folder", "url", "page", "book")
    }
}

data class CourseSection(
    val id: Long,
    val name: String,
    val number: Int = 0,
    val visible: Boolean = true,
    val modules: List<CourseModule> = emptyList()
)

/**
 * A course's full published state.
 *
 * Comes from `core_courseformat_get_state`, which on this Moodle install is the
 * only way in: `core_course_get_contents` is blocked over AJAX. That single
 * detail is what makes hidden assignments discoverable — the state call returns
 * *every* module, including the assignments that never reach the calendar.
 */
data class CourseState(
    val courseId: Long,
    val sections: List<CourseSection>,
    val modules: List<CourseModule>
)

/** A downloadable file attached to a course. */
data class ResourceFile(
    val fileName: String,
    val fileUrl: String,
    val moduleName: String,
    val modName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
)
