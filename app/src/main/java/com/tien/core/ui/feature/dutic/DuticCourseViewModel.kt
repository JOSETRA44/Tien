package com.tien.core.ui.feature.dutic

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tien.dutic.DuticClient
import com.tien.dutic.core.DuticResult
import com.tien.dutic.domain.model.CourseGrades
import com.tien.dutic.domain.model.CourseModule
import com.tien.dutic.domain.model.CourseSection
import com.tien.dutic.domain.model.DuticCourse
import com.tien.dutic.domain.model.DuticTask
import com.tien.dutic.domain.model.Participant
import com.tien.dutic.domain.repository.AssignmentDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DuticCourseUiState(
    val courseId: Long = 0,
    val course: DuticCourse? = null,
    val tab: DuticCourseTab = DuticCourseTab.TASKS,

    val tasks: List<DuticTask> = emptyList(),
    val materials: List<CourseModule> = emptyList(),
    /** Material grouped the way the course itself is organised. */
    val sections: List<CourseSection> = emptyList(),
    val teachers: List<Participant> = emptyList(),
    val classmates: List<Participant> = emptyList(),
    val grades: CourseGrades? = null,

    val openTaskId: Long? = null,
    val openTaskDetail: AssignmentDetail? = null,
    val isLoadingDetail: Boolean = false,

    /**
     * Which sections have finished loading.
     *
     * Tracked per section rather than with one global flag because each tab
     * costs a separate request: a spinner over the whole screen while only
     * "Gente" is still loading would hide tasks that already arrived.
     */
    val loaded: Set<DuticCourseTab> = emptySet(),
    val loading: Set<DuticCourseTab> = emptySet(),
    val failure: DuticFailure? = null
) {
    val title: String get() = course?.fullName.orEmpty()

    val isLoading: Boolean get() = tab in loading && tab !in loaded

    val peopleCount: Int get() = teachers.size + classmates.size

    val openTask: DuticTask? get() = tasks.firstOrNull { it.id == openTaskId }

    /**
     * Sections that actually contain material, in course order.
     *
     * Moodle courses carry empty sections and label-only ones; listing those
     * would be a screen of headings with nothing under them.
     */
    val materialSections: List<Pair<String, List<CourseModule>>>
        get() = sections
            .map { section ->
                val title = section.name.ifBlank { "Sin titulo" }
                title to materials.filter { it.sectionId == section.id }
            }
            .filter { it.second.isNotEmpty() }
}

/**
 * One course, and everything hanging off it.
 *
 * Reaches four of the module's tools — `get_course_tasks`,
 * `list_course_materials`, `list_participants`/`get_course_teachers`, and
 * `get_grades` — without the student ever meeting the word "tool".
 *
 * Sections load **on demand**, when their tab is first opened. Loading all four
 * up front would fire four requests for a screen where most students only ever
 * open one, over a phone connection, against a university server.
 */
class DuticCourseViewModel(
    private val client: DuticClient,
    private val courseId: Long
) : ViewModel() {
    private val _uiState = MutableStateFlow(DuticCourseUiState(courseId = courseId))
    val uiState: StateFlow<DuticCourseUiState> = _uiState.asStateFlow()

    init {
        loadCourseIdentity()
        onTabChange(DuticCourseTab.TASKS)
    }

    private fun loadCourseIdentity() {
        viewModelScope.launch {
            // The course list is already cached from the home screen in the
            // common case, so this is usually free.
            (client.listCourses() as? DuticResult.Ok)?.let { result ->
                _uiState.update { state ->
                    state.copy(course = result.value.firstOrNull { it.id == courseId })
                }
            }
        }
    }

    fun onTabChange(tab: DuticCourseTab) {
        _uiState.update { it.copy(tab = tab) }
        if (tab in _uiState.value.loaded || tab in _uiState.value.loading) return
        load(tab)
    }

    fun onRetry() {
        val tab = _uiState.value.tab
        _uiState.update { it.copy(failure = null, loaded = it.loaded - tab) }
        load(tab)
    }

    /**
     * Re-reads the roster from the server, ignoring the week-long store.
     *
     * The escape hatch for the one case the cache gets wrong: someone enrolled
     * after it was written. Without this the student would have to wait out the
     * week or clear the app data.
     */
    fun onRefreshPeople() {
        _uiState.update {
            it.copy(
                failure = null,
                loading = it.loading + DuticCourseTab.PEOPLE,
                loaded = it.loaded - DuticCourseTab.PEOPLE
            )
        }
        viewModelScope.launch { loadPeople(DuticCourseTab.PEOPLE, forceRefresh = true) }
    }

    private fun load(tab: DuticCourseTab) {
        _uiState.update { it.copy(loading = it.loading + tab, failure = null) }

        viewModelScope.launch {
            when (tab) {
                DuticCourseTab.TASKS -> fold(tab, client.courseTasks(courseId)) { tasks ->
                    _uiState.update { it.copy(tasks = tasks) }
                }

                DuticCourseTab.MATERIAL -> loadMaterial(tab)

                DuticCourseTab.PEOPLE -> loadPeople(tab)

                DuticCourseTab.GRADES -> fold(tab, client.courseGrades(courseId)) { grades ->
                    _uiState.update { it.copy(grades = grades) }
                }
            }
        }
    }

    /**
     * Material and its grouping arrive together.
     *
     * Both come from the same cached course state, so asking for the sections
     * costs nothing extra and turns a flat list into the structure the course
     * actually has.
     */
    private suspend fun loadMaterial(tab: DuticCourseTab) {
        val sections = (client.courseContents(courseId) as? DuticResult.Ok)?.value.orEmpty()
        fold(tab, client.courseMaterials(courseId)) { modules ->
            _uiState.update { it.copy(materials = modules, sections = sections) }
        }
    }

    /**
     * Teachers and classmates come from the same participant list, so they are
     * split here rather than fetched twice — `teachers` would otherwise be a
     * second identical request filtered differently.
     */
    private suspend fun loadPeople(tab: DuticCourseTab, forceRefresh: Boolean = false) {
        fold(tab, client.participants(courseId, forceRefresh)) { people ->
            _uiState.update { state ->
                state.copy(
                    teachers = people.filter { it.isTeacher },
                    classmates = people.filterNot { it.isTeacher }
                )
            }
        }
    }

    fun onOpenTask(task: DuticTask) {
        val cmid = task.cmid ?: task.id
        _uiState.update {
            it.copy(openTaskId = task.id, openTaskDetail = null, isLoadingDetail = true)
        }
        viewModelScope.launch {
            when (val result = client.assignmentDetail(cmid)) {
                is DuticResult.Ok -> _uiState.update {
                    it.copy(openTaskDetail = result.value, isLoadingDetail = false)
                }

                is DuticResult.Err -> _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun onCloseTask() {
        _uiState.update {
            it.copy(openTaskId = null, openTaskDetail = null, isLoadingDetail = false)
        }
    }

    private fun <T> fold(
        tab: DuticCourseTab,
        result: DuticResult<T>,
        onSuccess: (T) -> Unit
    ) {
        when (result) {
            is DuticResult.Ok -> {
                onSuccess(result.value)
                _uiState.update {
                    it.copy(loading = it.loading - tab, loaded = it.loaded + tab)
                }
            }

            is DuticResult.Err -> _uiState.update {
                it.copy(loading = it.loading - tab, failure = result.error.toFailure())
            }
        }
    }
}

/**
 * Finding a person across every enrolled course.
 *
 * Global rather than per-course on purpose: a student usually remembers the
 * name and not which course it belongs to. That is exactly why the CLI exposes
 * `find_person` as its own tool instead of a filter on the participant list.
 */
class DuticPeopleViewModel(
    private val client: DuticClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(DuticPeopleUiState())
    val uiState: StateFlow<DuticPeopleUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /**
     * Triggered by the search action, not by each keystroke.
     *
     * Searching means one participant-list request per enrolled course — a
     * dozen requests. Debouncing that would still fire it on every pause in
     * typing, so it waits for the user to say they mean it.
     */
    fun onSearch() {
        val query = _uiState.value.query.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(failure = SHORT_QUERY_HINT) }
            return
        }

        _uiState.update { it.copy(isSearching = true, failure = null, hasSearched = true) }

        viewModelScope.launch {
            when (val result = client.findPerson(query)) {
                is DuticResult.Ok -> _uiState.update {
                    it.copy(matches = result.value, isSearching = false)
                }

                is DuticResult.Err -> _uiState.update {
                    it.copy(isSearching = false, failure = result.error.toFailure())
                }
            }
        }
    }

    fun onLoadProfile(userId: Long, courseId: Long?) {
        _uiState.update { it.copy(isLoadingProfile = true, profile = null, failure = null) }
        viewModelScope.launch {
            when (val result = client.personProfile(userId, courseId?.takeIf { id -> id > 0 })) {
                is DuticResult.Ok -> _uiState.update {
                    it.copy(profile = result.value, isLoadingProfile = false)
                }

                is DuticResult.Err -> _uiState.update {
                    it.copy(isLoadingProfile = false, failure = result.error.toFailure())
                }
            }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3

        val SHORT_QUERY_HINT = DuticFailure(
            title = "Escribe un poco más",
            body = "Busca con al menos tres letras del nombre."
        )
    }
}

@Immutable
data class DuticPeopleUiState(
    val query: String = "",
    val matches: List<com.tien.dutic.domain.model.PersonMatch> = emptyList(),
    val profile: com.tien.dutic.domain.model.PersonProfile? = null,
    val isSearching: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val hasSearched: Boolean = false,
    val failure: DuticFailure? = null
) {
    val isEmpty: Boolean get() = matches.isEmpty()
}
