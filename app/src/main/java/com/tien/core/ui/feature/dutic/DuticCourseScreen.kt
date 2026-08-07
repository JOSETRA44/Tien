package com.tien.core.ui.feature.dutic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tien.core.core.time.DateTimeLabels
import com.tien.core.core.time.TienClock
import com.tien.core.ui.designsystem.component.EmptyState
import com.tien.core.ui.designsystem.component.ErrorState
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.component.SectionEyebrow
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * One course.
 *
 * Four tools behind four words. A student who wants "the slides for Cálculo"
 * taps the course and then "Material" — they never learn that
 * `list_course_materials` exists, which is the point.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DuticCourseScreen(
    viewModel: DuticCourseViewModel,
    clock: TienClock,
    labels: DateTimeLabels,
    onBack: () -> Unit,
    onOpenPerson: (userId: Long, courseId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val now = clock.nowEpochSeconds()
    val todayEnd = clock.dayRange(clock.today()).endExclusive

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TienTheme.spacing.snug,
                    end = TienTheme.spacing.gutter,
                    top = TienTheme.spacing.snug
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Volver a cursos"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title.ifBlank { "Curso" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    // Course names at this university run long ("Tecnologías de
                    // Información y Comunicación II"), so this always wraps.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                uiState.course?.shortName?.takeIf { it.isNotBlank() }?.let {
                    SectionEyebrow(text = it)
                }
            }
        }

        DuticSegmentedTabs(
            options = DuticCourseTab.entries,
            selected = uiState.tab,
            label = { it.label },
            onSelect = viewModel::onTabChange
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.failure != null -> ErrorState(
                    title = uiState.failure!!.title,
                    body = uiState.failure!!.body,
                    onRetry = viewModel::onRetry
                )

                uiState.isLoading -> LoadingState()

                else -> when (uiState.tab) {
                    DuticCourseTab.TASKS -> TasksTab(
                        uiState, labels, now, todayEnd, viewModel::onOpenTask
                    )
                    DuticCourseTab.MATERIAL -> MaterialTab(uiState)
                    DuticCourseTab.PEOPLE -> PeopleTab(
                        uiState, onOpenPerson, viewModel::onRefreshPeople
                    )
                    DuticCourseTab.GRADES -> GradesTab(uiState)
                }
            }
        }
    }

    val openTask = uiState.openTask
    if (openTask != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DuticTaskDetailSheet(
            sheetState = sheetState,
            task = openTask,
            detail = uiState.openTaskDetail,
            isLoading = uiState.isLoadingDetail,
            labels = labels,
            onDismiss = viewModel::onCloseTask
        )
    }
}

@Composable
private fun TasksTab(
    uiState: DuticCourseUiState,
    labels: DateTimeLabels,
    now: Long,
    todayEnd: Long,
    onOpenTask: (com.tien.dutic.domain.model.DuticTask) -> Unit
) {
    if (uiState.tasks.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.TaskAlt,
            title = "Sin tareas",
            body = "Este curso no tiene tareas publicadas todavía."
        )
        return
    }

    LazyColumn(
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.base),
        modifier = Modifier.fillMaxSize()
    ) {
        items(uiState.tasks, key = { it.id }) { task ->
            DuticTaskCard(
                task = task,
                labels = labels,
                nowEpochSeconds = now,
                todayEndEpochSeconds = todayEnd,
                onClick = { onOpenTask(task) }
            )
        }
    }
}

@Composable
private fun MaterialTab(uiState: DuticCourseUiState) {
    if (uiState.materials.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = "Sin material",
            body = "El docente aún no ha subido archivos ni enlaces a este curso."
        )
        return
    }

    val grouped = uiState.materialSections

    LazyColumn(
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug),
        modifier = Modifier.fillMaxSize()
    ) {
        // Grouped by the course's own sections when they are known. A teacher
        // organised the material into weeks or units for a reason, and a flat
        // list throws that away.
        if (grouped.isEmpty()) {
            items(uiState.materials, key = { it.cmid }) { module ->
                // Not tappable yet: opening one means downloading it, and
                // downloads need scoped storage plus a foreground service to
                // survive the screen locking. A row that looks tappable and
                // does nothing is worse than one that plainly lists what is
                // there.
                MaterialRow(module = module, onClick = null)
            }
        } else {
            grouped.forEach { (title, modules) ->
                item(key = "section_$title") {
                    SectionEyebrow(
                        text = title,
                        modifier = Modifier.padding(
                            top = TienTheme.spacing.snug,
                            bottom = TienTheme.spacing.tight
                        )
                    )
                }
                items(modules, key = { it.cmid }) { module ->
                    MaterialRow(module = module, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(
    uiState: DuticCourseUiState,
    onOpenPerson: (userId: Long, courseId: Long) -> Unit,
    onRefresh: () -> Unit
) {
    if (uiState.peopleCount == 0) {
        EmptyState(
            icon = Icons.Outlined.Groups,
            title = "Sin lista de participantes",
            // Names the two real causes instead of asserting the private one:
            // the roster may genuinely be restricted, or the course may filter
            // it by group. Either way, retrying is the move.
            body = "Este curso no la comparte, o la muestra filtrada por grupo.",
            actionLabel = "Volver a intentar",
            onAction = onRefresh
        )
        return
    }

    LazyColumn(
        contentPadding = listPadding(),
        verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug),
        modifier = Modifier.fillMaxSize()
    ) {
        // Teachers first, always. They are who a student needs to reach.
        if (uiState.teachers.isNotEmpty()) {
            item(key = "eyebrow_teachers") {
                SectionEyebrow(
                    text = if (uiState.teachers.size == 1) "Docente" else "Docentes",
                    modifier = Modifier.padding(bottom = TienTheme.spacing.tight)
                )
            }
            items(uiState.teachers, key = { "t_${it.userId}" }) { person ->
                PersonRow(
                    participant = person,
                    onClick = { onOpenPerson(person.userId, uiState.courseId) }
                )
            }
        }

        if (uiState.classmates.isNotEmpty()) {
            item(key = "eyebrow_classmates") {
                SectionEyebrow(
                    text = "${uiState.classmates.size} compañeros",
                    modifier = Modifier.padding(
                        top = TienTheme.spacing.base,
                        bottom = TienTheme.spacing.tight
                    )
                )
            }
            items(uiState.classmates, key = { "c_${it.userId}" }) { person ->
                PersonRow(
                    participant = person,
                    onClick = { onOpenPerson(person.userId, uiState.courseId) }
                )
            }
        }

        // The roster is stored for a week, so there has to be a way to say
        // "someone enrolled since" without clearing the app data.
        item(key = "refresh_roster") {
            TextButton(
                onClick = onRefresh,
                modifier = Modifier.padding(top = TienTheme.spacing.snug)
            ) {
                Text("Actualizar lista")
            }
        }
    }
}

@Composable
private fun GradesTab(uiState: DuticCourseUiState) {
    val grades = uiState.grades

    if (grades == null || grades.items.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Grading,
            title = "Sin notas todavía",
            body = "Aún no hay nada calificado en este curso."
        )
        return
    }

    LazyColumn(
        contentPadding = listPadding(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "summary") {
            SectionEyebrow(
                text = "${grades.gradedCount} calificadas · ${grades.pendingCount} pendientes",
                modifier = Modifier.padding(bottom = TienTheme.spacing.snug)
            )
        }
        items(grades.items, key = { it.name }) { item ->
            GradeRow(item = item)
        }
    }
}
