package com.tien.core.ui.feature.dutic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.core.time.TienClock
import com.tien.core.ui.designsystem.component.EmptyState
import com.tien.core.ui.designsystem.component.ErrorState
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.component.SectionEyebrow
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.dutic.core.DuticConfig

/**
 * The aula virtual.
 *
 * ### What the screen is for
 * One question: *do I owe anything?* Everything above the list exists to answer
 * it before the student reads a single row, and the answer that matters most is
 * the one Moodle will not give them — how much of their pending work its own
 * calendar leaves out.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DuticScreen(
    viewModel: DuticViewModel,
    clock: TienClock,
    labels: DateTimeLabels,
    snackbarHostState: SnackbarHostState,
    onOpenCourse: (Long) -> Unit,
    onOpenPeopleSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogin by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DuticEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is DuticEvent.RequireSignIn -> showLogin = true
            }
        }
    }

    val now = clock.nowEpochSeconds()
    val todayEnd = clock.dayRange(clock.today()).endExclusive

    // One root owning `modifier`, with the three states inside it. Passing the
    // caller's modifier into each branch instead would apply the same instance
    // more than once — harmless while the branches stay exclusive, and a real
    // bug the moment one of them stops being.
    Box(modifier = modifier.fillMaxSize()) {
        when {
            showLogin -> DuticLoginScreen(
                loginUrl = DuticConfig.loginUrl(),
                onCaptured = { capture ->
                    showLogin = false
                    viewModel.onLoginCaptured(capture)
                }
            )

            !uiState.isSignedIn -> SignedOutState(onSignIn = { showLogin = true })

            else -> SignedInContent(
                uiState = uiState,
                labels = labels,
                nowEpochSeconds = now,
                todayEndEpochSeconds = todayEnd,
                onRefresh = viewModel::refresh,
                onSignOut = viewModel::onSignOut,
                onFilterChange = viewModel::onFilterChange,
                onOpenTask = viewModel::onOpenTask,
                onHomeTabChange = viewModel::onHomeTabChange,
                onRetry = viewModel::onRetry,
                onSignIn = { showLogin = true },
                onOpenCourse = onOpenCourse,
                onOpenPeopleSearch = onOpenPeopleSearch,
                onCloseTask = viewModel::onCloseTask
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SignedInContent(
    uiState: DuticUiState,
    labels: DateTimeLabels,
    nowEpochSeconds: Long,
    todayEndEpochSeconds: Long,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onFilterChange: (DuticFilter) -> Unit,
    onOpenTask: (com.tien.dutic.domain.model.DuticTask) -> Unit,
    onHomeTabChange: (DuticHomeTab) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onOpenCourse: (Long) -> Unit,
    onOpenPeopleSearch: () -> Unit,
    onCloseTask: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DuticHeader(
            summary = uiState.summary,
            semester = uiState.semester,
            displayName = uiState.displayName,
            onRefresh = onRefresh,
            onSignOut = onSignOut
        )

        // A thin line over data already on screen, rather than a spinner
        // replacing it: the slow sweep runs for seconds and the first pass is
        // real information worth reading meanwhile.
        AnimatedVisibility(
            visible = uiState.isRefreshing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        DuticSegmentedTabs(
            options = DuticHomeTab.entries,
            selected = uiState.homeTab,
            label = { it.label },
            onSelect = onHomeTabChange
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.failure != null -> ErrorState(
                    title = uiState.failure!!.title,
                    body = uiState.failure!!.body,
                    onRetry = if (uiState.failure!!.needsSignIn) onSignIn else onRetry
                )

                uiState.isLoading -> LoadingState()

                else -> when (uiState.homeTab) {
                    DuticHomeTab.TASKS -> TasksSection(
                        uiState = uiState,
                        labels = labels,
                        nowEpochSeconds = nowEpochSeconds,
                        todayEndEpochSeconds = todayEndEpochSeconds,
                        onFilterChange = onFilterChange,
                        onOpenTask = onOpenTask
                    )

                    DuticHomeTab.COURSES -> CoursesSection(
                        uiState = uiState,
                        onOpenCourse = onOpenCourse,
                        onOpenPeopleSearch = onOpenPeopleSearch
                    )

                    DuticHomeTab.GRADES -> GradesSection(uiState = uiState)
                }
            }
        }
    }

    // At the screen root, not inside TasksSection: the sheet must survive the
    // student switching to Cursos while its brief is still loading.
    val openTask = uiState.openTask
    if (openTask != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DuticTaskDetailSheet(
            sheetState = sheetState,
            task = openTask,
            detail = uiState.openTaskDetail,
            isLoading = uiState.isLoadingDetail,
            labels = labels,
            onDismiss = onCloseTask
        )
    }
}

@Composable
private fun TasksSection(
    uiState: DuticUiState,
    labels: DateTimeLabels,
    nowEpochSeconds: Long,
    todayEndEpochSeconds: Long,
    onFilterChange: (DuticFilter) -> Unit,
    onOpenTask: (com.tien.dutic.domain.model.DuticTask) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        FilterRow(
            current = uiState.filter,
            hiddenCount = uiState.summary.hidden,
            onChange = onFilterChange
        )

        if (uiState.isEmpty) {
            EmptyForFilter(uiState.filter, uiState.summary)
            return@Column
        }

        LazyColumn(
            contentPadding = listPadding(),
            verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.base),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.visibleTasks, key = { it.id }) { task ->
                DuticTaskCard(
                    task = task,
                    labels = labels,
                    nowEpochSeconds = nowEpochSeconds,
                    todayEndEpochSeconds = todayEndEpochSeconds,
                    onClick = { onOpenTask(task) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun CoursesSection(
    uiState: DuticUiState,
    onOpenCourse: (Long) -> Unit,
    onOpenPeopleSearch: () -> Unit
) {
    if (uiState.courses.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.School,
            title = "Sin cursos",
            body = "Todavia no apareces matriculado en ningun curso este semestre."
        )
        return
    }

    LazyColumn(
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug),
        modifier = Modifier.fillMaxSize()
    ) {
        // Finding a person spans every course, so it cannot live inside one --
        // it sits above the list instead.
        item(key = "people_search") {
            PeopleSearchEntry(onClick = onOpenPeopleSearch)
            Spacer(Modifier.height(TienTheme.spacing.base))
        }

        items(uiState.courses, key = { it.id }) { course ->
            CourseRow(course = course, onClick = { onOpenCourse(course.id) })
        }
    }
}

@Composable
private fun PeopleSearchEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TienTheme.spacing.comfy),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.base)
        ) {
            Icon(
                Icons.Outlined.PersonSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Buscar una persona",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Companeros y docentes de todos tus cursos",
                    style = TienTextStyles.meta,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun GradesSection(uiState: DuticUiState) {
    if (uiState.isLoadingGrades) {
        LoadingState()
        return
    }

    val courses = uiState.gradedCourses
    if (courses.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Grading,
            title = "Sin notas todavia",
            body = "Cuando tus docentes califiquen algo, aparecera aqui."
        )
        return
    }

    LazyColumn(
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.loose),
        modifier = Modifier.fillMaxSize()
    ) {
        items(courses, key = { it.courseId }) { course ->
            Column {
                Text(
                    text = course.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SectionEyebrow(
                    text = "${course.gradedCount} calificadas - ${course.pendingCount} pendientes"
                )
                // Only the marked items: an unmarked list under a course
                // heading reads as bad news that has not happened yet.
                course.items.filter { it.isGraded }.forEach { item ->
                    GradeRow(item = item)
                }
            }
        }
    }
}

/**
 * The header, and the one idea this feature is built on.
 *
 * The pending count is the number a student expects. The line under it is the
 * one they cannot get anywhere else: how many of those the calendar simply does
 * not show. Setting the two side by side is the whole product in one sentence.
 */
@Composable
private fun DuticHeader(
    summary: DuticSummary,
    semester: String?,
    displayName: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    val extended = TienTheme.extendedColors

    // Counting up rather than snapping: the number changes when the sweep
    // finishes, and a silent jump from 1 to 4 reads as a glitch. Watching it
    // climb reads as discovery, which is what actually happened.
    val animatedPending by animateIntAsState(
        targetValue = summary.pending,
        animationSpec = tween(durationMillis = 520),
        label = "pendingCount"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TienTheme.spacing.gutter,
                end = TienTheme.spacing.snug,
                top = TienTheme.spacing.snug,
                bottom = TienTheme.spacing.base
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aula virtual",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                SectionEyebrow(
                    text = listOfNotNull(displayName, semester)
                        .joinToString(" · ")
                        .ifBlank { "UNSA" }
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
            }
            IconButton(onClick = onSignOut) {
                Icon(Icons.Outlined.Logout, contentDescription = "Cerrar sesión")
            }
        }

        Spacer(Modifier.height(TienTheme.spacing.comfy))

        if (summary.isClear) {
            Text(
                text = "Nada pendiente",
                style = TienTextStyles.metric,
                color = extended.scheduled
            )
            SectionEyebrow(text = "Estás al día")
            return@Column
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = animatedPending.toString(),
                style = TienTextStyles.metric,
                color = if (summary.overdue > 0) extended.overdue else extended.today
            )
            Spacer(Modifier.width(TienTheme.spacing.snug))
            // weight(1f) so the labels give way rather than shoving the metric
            // off screen: at 200% font scaling the number is ~56sp wide and
            // "SIN ENTREGAR" no longer fits beside it.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 3.dp)
            ) {
                SectionEyebrow(text = if (summary.pending == 1) "Sin entregar" else "Sin entregar")
                if (summary.overdue > 0) {
                    SectionEyebrow(
                        text = if (summary.overdue == 1) "1 vencida" else "${summary.overdue} vencidas",
                        color = extended.overdue
                    )
                }
            }
        }

        // The reveal. Phrased as what Moodle does, not as an app feature.
        AnimatedVisibility(visible = summary.hasHidden, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier.padding(top = TienTheme.spacing.snug),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(extended.overdue)
                )
                Text(
                    text = if (summary.hidden == 1) {
                        "El calendario no te muestra 1 de ellas"
                    } else {
                        "El calendario no te muestra ${summary.hidden} de ellas"
                    },
                    style = TienTextStyles.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    current: DuticFilter,
    hiddenCount: Int,
    onChange: (DuticFilter) -> Unit
) {
    // Scrollable, not a fixed Row. Three chips fit in Spanish at the default
    // font size and stop fitting at 200% scaling or on a 320dp screen — at which
    // point a plain Row clips the last one with no way to reach it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = TienTheme.spacing.gutter,
                vertical = TienTheme.spacing.snug
            ),
        horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
    ) {
        DuticFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == current,
                onClick = { onChange(filter) },
                label = {
                    Text(
                        // The count rides on the chip that needs it: "Ocultas"
                        // is the tab a student is deciding whether to open.
                        if (filter == DuticFilter.HIDDEN && hiddenCount > 0) {
                            "${filter.label} · $hiddenCount"
                        } else {
                            filter.label
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun EmptyForFilter(filter: DuticFilter, summary: DuticSummary) {
    when (filter) {
        DuticFilter.PENDING -> EmptyState(
            icon = Icons.Outlined.TaskAlt,
            title = "Nada por entregar",
            body = "No tienes entregas pendientes. Vuelve a revisar cuando publiquen algo nuevo."
        )

        DuticFilter.HIDDEN -> EmptyState(
            icon = Icons.Outlined.TaskAlt,
            title = "El calendario no te esconde nada",
            body = "Todas tus tareas pendientes aparecen también en el calendario del aula virtual."
        )

        DuticFilter.ALL -> EmptyState(
            icon = Icons.Outlined.School,
            title = "Sin tareas",
            body = if (summary.courses > 0) {
                "Tus ${summary.courses} cursos no tienen tareas publicadas todavía."
            } else {
                "Todavía no aparecen cursos en tu aula virtual."
            }
        )
    }
}

/**
 * Before signing in.
 *
 * States the one thing the student gets that they cannot get from the aula
 * virtual's own app — otherwise this is just another login wall.
 */
@Composable
private fun SignedOutState(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TienTheme.spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.School,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(TienTheme.spacing.loose))

        Text(
            text = "Conecta tu aula virtual",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(TienTheme.spacing.snug))

        Text(
            text = "Verás tus entregas de la UNSA — incluidas las que el calendario " +
                "de Moodle deja fuera porque ya vencieron o no tienen fecha.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(TienTheme.spacing.loose))

        Button(onClick = onSignIn) {
            Text("Iniciar sesión")
        }

        Spacer(Modifier.height(TienTheme.spacing.base))

        Text(
            // Says plainly where the credentials go. A student handing over
            // university credentials deserves to be told.
            text = "Entrarás con tu correo UNSA en la página de Google. " +
                "Tien no ve tu contraseña.",
            style = TienTextStyles.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
