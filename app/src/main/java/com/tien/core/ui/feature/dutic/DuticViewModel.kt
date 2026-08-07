package com.tien.core.ui.feature.dutic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tien.core.core.time.TienClock
import com.tien.dutic.DuticClient
import com.tien.dutic.auth.LoginCapture
import com.tien.dutic.core.DuticError
import com.tien.dutic.core.DuticResult
import com.tien.dutic.domain.model.DuticTask
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation logic for the aula virtual section.
 *
 * ### The two-pass load
 * Answering "do I owe anything?" accurately costs one HTTP request per
 * assignment, because Moodle only admits submission state on the assignment's
 * own page. Waiting for all of that before showing anything would mean a blank
 * screen for several seconds on a phone connection.
 *
 * So it loads twice: the calendar's own list first — one call, near-instant, and
 * enough to paint something real — then the full sweep behind it, which corrects
 * the numbers and reveals what the calendar omitted. The second pass sets
 * [DuticUiState.isRefreshing], never `isLoading`, so the list the user is already
 * reading is never replaced by a spinner.
 */
class DuticViewModel(
    private val client: DuticClient,
    private val clock: TienClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuticUiState())
    val uiState: StateFlow<DuticUiState> = _uiState.asStateFlow()

    private val _events = Channel<DuticEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        client.isSignedIn
            .distinctUntilChanged()
            .onEach { signedIn ->
                _uiState.update { it.copy(isSignedIn = signedIn) }
                if (signedIn) refresh() else clearData()
            }
            .launchIn(viewModelScope)
    }

    /** Runs both passes. Safe to call from pull-to-refresh. */
    fun refresh() {
        viewModelScope.launch {
            val hadData = _uiState.value.tasks.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !hadData,
                    isRefreshing = hadData,
                    failure = null
                )
            }

            loadIdentity()

            // Pass one: what the calendar shows. Cheap enough to paint with.
            when (val quick = client.upcomingTasks()) {
                is DuticResult.Ok -> render(quick.value, isComplete = false)
                is DuticResult.Err -> {
                    // A failure here is usually the session; report it now
                    // rather than after the slow sweep also fails.
                    handleFailure(quick.error)
                    return@launch
                }
            }

            // Pass two: the sweep that finds what the calendar hides.
            when (val full = client.listTasks(enrich = true)) {
                is DuticResult.Ok -> render(full.value, isComplete = true)
                is DuticResult.Err -> handleFailure(full.error)
            }

            loadCourses()
        }
    }

    private suspend fun loadIdentity() {
        when (val status = client.sessionStatus()) {
            else -> _uiState.update { it.copy(semester = status.semester) }
        }
        (client.whoAmI() as? DuticResult.Ok)?.let { result ->
            _uiState.update { it.copy(displayName = result.value.takeIf { n -> n.isNotBlank() }) }
        }
    }

    private suspend fun loadCourses() {
        when (val result = client.listCourses()) {
            is DuticResult.Ok -> _uiState.update { state ->
                state.copy(
                    courses = result.value,
                    summary = state.summary.copy(courses = result.value.size)
                )
            }
            // Courses are context, not the answer to the question this screen
            // asks, so failing to load them must not blank the tasks.
            is DuticResult.Err -> Unit
        }
    }

    /**
     * @param isComplete false for the calendar-only pass. The hidden count is
     *   meaningless until the sweep has run — every task looks visible when the
     *   calendar is the only source — so it is left at zero rather than shown
     *   as a confident "0 ocultas".
     */
    private fun render(tasks: List<DuticTask>, isComplete: Boolean) {
        val now = clock.nowEpochSeconds()
        val pending = tasks.filter { it.isPending }

        _uiState.update { state ->
            state.copy(
                tasks = tasks,
                summary = state.summary.copy(
                    pending = pending.size,
                    visibleInCalendar = if (isComplete) {
                        pending.count { !it.hidden }
                    } else {
                        pending.size
                    },
                    overdue = pending.count { it.isOverdue(now) }
                ),
                isLoading = false,
                isRefreshing = !isComplete,
                failure = null
            )
        }
    }

    private suspend fun handleFailure(error: DuticError) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                failure = error.toFailure()
            )
        }
        if (error is DuticError.SessionExpired || error is DuticError.NotSignedIn) {
            _events.send(DuticEvent.RequireSignIn)
        }
    }

    private fun clearData() {
        _uiState.update {
            DuticUiState(isSignedIn = false)
        }
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    fun onFilterChange(filter: DuticFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    /**
     * Grades load the first time their tab is opened, not with the screen.
     * Fetching them up front means one HTML page per enrolled course for a
     * section most students visit occasionally.
     */
    fun onHomeTabChange(tab: DuticHomeTab) {
        _uiState.update { it.copy(homeTab = tab) }
        if (tab == DuticHomeTab.GRADES && _uiState.value.grades.isEmpty()) {
            loadGrades()
        }
    }

    private fun loadGrades() {
        _uiState.update { it.copy(isLoadingGrades = true) }
        viewModelScope.launch {
            when (val result = client.allGrades()) {
                is DuticResult.Ok -> _uiState.update {
                    it.copy(grades = result.value, isLoadingGrades = false)
                }

                is DuticResult.Err -> _uiState.update {
                    it.copy(isLoadingGrades = false, failure = result.error.toFailure())
                }
            }
        }
    }

    /**
     * Opens an assignment's brief.
     *
     * The task is shown immediately from data already in hand; only the brief
     * itself is fetched, so the sheet never opens onto a spinner alone.
     */
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

                is DuticResult.Err -> _uiState.update {
                    it.copy(isLoadingDetail = false)
                }
            }
        }
    }

    fun onCloseTask() {
        _uiState.update {
            it.copy(openTaskId = null, openTaskDetail = null, isLoadingDetail = false)
        }
    }

    fun onLoginCaptured(capture: LoginCapture) {
        viewModelScope.launch {
            when (val result = client.completeLogin(capture)) {
                is DuticResult.Ok -> refresh()
                is DuticResult.Err -> _events.send(
                    DuticEvent.ShowMessage(result.error.toFailure().body)
                )
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            client.signOut()
            clearData()
        }
    }

    fun onRetry() = refresh()
}

/**
 * Failure copy.
 *
 * Each one names what happened and what fixes it. "Error de red" tells a student
 * nothing they can act on; "no hay conexión, vuelve a intentarlo" does.
 */
internal fun DuticError.toFailure(): DuticFailure = when (this) {
    is DuticError.NotSignedIn -> DuticFailure(
        title = "Conecta tu aula virtual",
        body = "Inicia sesión para ver tus entregas.",
        needsSignIn = true
    )

    is DuticError.SessionExpired -> DuticFailure(
        title = "La sesión caducó",
        body = "El aula virtual cerró tu sesión. Vuelve a entrar para seguir.",
        needsSignIn = true
    )

    is DuticError.Network -> DuticFailure(
        title = "Sin conexión con el aula virtual",
        body = "Revisa tu conexión y vuelve a intentarlo."
    )

    is DuticError.MoodleApi -> DuticFailure(
        title = "El aula virtual rechazó la consulta",
        body = message.ifBlank { "Vuelve a intentarlo en un momento." }
    )

    is DuticError.Unreadable -> DuticFailure(
        title = "Respuesta ilegible",
        body = "El aula virtual respondió en un formato que esta versión no entiende."
    )
}
