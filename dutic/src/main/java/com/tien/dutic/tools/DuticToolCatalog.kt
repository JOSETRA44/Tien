package com.tien.dutic.tools

/**
 * The tool surface, declared.
 *
 * ### Why this exists
 * The CLI and MCP server expose 24 named tools. Porting them to a phone as a
 * handful of screens would quietly lose that surface: nobody could tell which
 * capabilities made the crossing and which did not, and the next person would
 * re-implement one that already existed.
 *
 * So the catalogue is data. It keeps the mobile port honest about parity, gives
 * the UI something to enumerate ("what can this do?"), and makes adding a tool a
 * matter of adding an entry plus a method on [com.tien.dutic.DuticClient] rather
 * than inventing a place to put it.
 *
 * Names match the MCP exactly, so a question asked of the CLI and a tap in the
 * app are traceably the same operation.
 */
data class DuticTool(
    /** MCP name, e.g. `dutic_list_tasks`. */
    val name: String,
    val category: ToolCategory,
    val summary: String,
    val status: ToolStatus
)

enum class ToolCategory {
    SESSION,
    COURSES,
    TASKS,
    GRADES,
    PEOPLE,
    FILES
}

enum class ToolStatus {
    /** Available on mobile. */
    AVAILABLE,

    /**
     * Not ported yet. Recorded rather than omitted so the gap is visible and
     * deliberate instead of looking like an oversight.
     */
    PENDING
}

object DuticToolCatalog {

    val tools: List<DuticTool> = listOf(
        // ── Session ─────────────────────────────────────────────────────────
        DuticTool(
            name = "dutic_session_status",
            category = ToolCategory.SESSION,
            summary = "¿Hay sesión válida y de qué semestre?",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_whoami",
            category = ToolCategory.SESSION,
            summary = "Quién está conectado en el aula virtual",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_refresh_session",
            category = ToolCategory.SESSION,
            summary = "Renovar la sesión abriendo el inicio de sesión",
            status = ToolStatus.AVAILABLE
        ),

        // ── Courses ─────────────────────────────────────────────────────────
        DuticTool(
            name = "dutic_list_courses",
            category = ToolCategory.COURSES,
            summary = "Cursos matriculados",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_get_course_contents",
            category = ToolCategory.COURSES,
            summary = "Secciones y módulos de un curso",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_list_course_materials",
            category = ToolCategory.COURSES,
            summary = "Material de estudio de un curso",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_list_course_files",
            category = ToolCategory.FILES,
            summary = "Archivos descargables de un curso",
            status = ToolStatus.AVAILABLE
        ),

        // ── Tasks ───────────────────────────────────────────────────────────
        DuticTool(
            name = "dutic_list_tasks",
            category = ToolCategory.TASKS,
            summary = "Todas las tareas, incluidas las que el calendario oculta",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_get_course_tasks",
            category = ToolCategory.TASKS,
            summary = "Tareas de un curso concreto",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_get_assignment_detail",
            category = ToolCategory.TASKS,
            summary = "Consigna, estado de entrega, nota y adjuntos",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_check_changes",
            category = ToolCategory.TASKS,
            summary = "Novedades desde la última revisión",
            status = ToolStatus.AVAILABLE
        ),

        // ── Grades ──────────────────────────────────────────────────────────
        DuticTool(
            name = "dutic_get_grades",
            category = ToolCategory.GRADES,
            summary = "Notas de un curso o de todos",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_compare_grades",
            category = ToolCategory.GRADES,
            summary = "Comparar notas entre cursos",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_get_sisacad_grades",
            category = ToolCategory.GRADES,
            summary = "Notas oficiales de SISACAD",
            // SISACAD is a separate system with its own sign-in; porting it is a
            // second authentication flow, not a second endpoint.
            status = ToolStatus.PENDING
        ),

        // ── People ──────────────────────────────────────────────────────────
        DuticTool(
            name = "dutic_list_participants",
            category = ToolCategory.PEOPLE,
            summary = "Compañeros y docentes de un curso",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_get_course_teachers",
            category = ToolCategory.PEOPLE,
            summary = "Sólo los docentes de un curso",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_find_person",
            category = ToolCategory.PEOPLE,
            summary = "Buscar a alguien en todos los cursos",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_get_person_profile",
            category = ToolCategory.PEOPLE,
            summary = "Perfil completo de una persona",
            status = ToolStatus.AVAILABLE
        ),

        // ── Files ───────────────────────────────────────────────────────────
        DuticTool(
            name = "dutic_fetch_page",
            category = ToolCategory.FILES,
            summary = "Descargar una página del aula virtual",
            status = ToolStatus.AVAILABLE
        ),
        DuticTool(
            name = "dutic_read_resource",
            category = ToolCategory.FILES,
            summary = "Leer un recurso como texto",
            status = ToolStatus.PENDING
        ),
        DuticTool(
            name = "dutic_download_file",
            category = ToolCategory.FILES,
            // Downloads need scoped storage and a foreground service to survive
            // the screen locking — a piece of mobile plumbing, not a port.
            summary = "Guardar un archivo en el dispositivo",
            status = ToolStatus.PENDING
        ),
        DuticTool(
            name = "dutic_pull_course_files",
            category = ToolCategory.FILES,
            summary = "Descargar todos los archivos de un curso",
            status = ToolStatus.PENDING
        ),
        DuticTool(
            name = "dutic_study_course",
            category = ToolCategory.FILES,
            summary = "Preparar el material de un curso para estudiar",
            status = ToolStatus.PENDING
        ),
        DuticTool(
            name = "dutic_pdf_to_markdown",
            category = ToolCategory.FILES,
            // Needs a PDF engine in the APK; deliberately deferred rather than
            // shipping a half-working extractor.
            summary = "Convertir un PDF a texto",
            status = ToolStatus.PENDING
        )
    )

    val available: List<DuticTool> get() = tools.filter { it.status == ToolStatus.AVAILABLE }

    val pending: List<DuticTool> get() = tools.filter { it.status == ToolStatus.PENDING }

    fun byCategory(category: ToolCategory): List<DuticTool> =
        tools.filter { it.category == category }

    /** How much of the CLI made the crossing, as a fraction. */
    val coverage: Float get() = available.size.toFloat() / tools.size
}
