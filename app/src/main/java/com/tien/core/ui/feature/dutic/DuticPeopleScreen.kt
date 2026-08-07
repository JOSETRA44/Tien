package com.tien.core.ui.feature.dutic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tien.core.ui.designsystem.component.EmptyState
import com.tien.core.ui.designsystem.component.ErrorState
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.component.SectionEyebrow
import com.tien.core.ui.designsystem.theme.TienTheme

/**
 * Finding someone across every course.
 *
 * The search is explicit — a button and the keyboard's search key, not
 * search-as-you-type. Each run costs one participant-list request per enrolled
 * course, so firing it on every keystroke would mean a dozen requests per
 * letter.
 */
@Composable
fun DuticPeopleScreen(
    viewModel: DuticPeopleViewModel,
    onBack: () -> Unit,
    onOpenPerson: (userId: Long, courseId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TienTheme.spacing.snug, top = TienTheme.spacing.snug),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = "Buscar persona",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Nombre de un compañero o docente") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSearch() }),
            supportingText = {
                // Says up front that this is a whole-account search, so the
                // wait that follows is expected rather than a hang.
                Text("Se busca en todos tus cursos")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = TienTheme.spacing.gutter,
                    vertical = TienTheme.spacing.snug
                )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.failure != null -> ErrorState(
                    title = uiState.failure!!.title,
                    body = uiState.failure!!.body,
                    onRetry = viewModel::onSearch
                )

                uiState.isSearching -> LoadingState()

                !uiState.hasSearched -> EmptyState(
                    icon = Icons.Outlined.PersonSearch,
                    title = "¿A quién buscas?",
                    body = "Escribe un nombre y toca buscar. Te diré en qué cursos está."
                )

                uiState.isEmpty -> EmptyState(
                    icon = Icons.Outlined.PersonSearch,
                    title = "Nadie con ese nombre",
                    body = "No aparece en ninguno de tus cursos. Prueba con el apellido."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = TienTheme.spacing.gutter,
                        end = TienTheme.spacing.gutter,
                        top = TienTheme.spacing.base,
                        bottom = TienTheme.spacing.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Keyed on the person alone. The previous key paired them
                    // with a course, so a classmate shared across four courses
                    // rendered four identical rows.
                    items(items = uiState.matches, key = { it.userId }) { match ->
                        PersonMatchRow(
                            match = match,
                            onClick = {
                                onOpenPerson(match.userId, match.contextCourseId ?: 0L)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** A person's profile. */
@Composable
fun DuticProfileScreen(
    viewModel: DuticPeopleViewModel,
    userId: Long,
    courseId: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userId, courseId) {
        viewModel.onLoadProfile(userId, courseId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TienTheme.spacing.snug, top = TienTheme.spacing.snug),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = uiState.profile?.fullName.orEmpty().ifBlank { "Perfil" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoadingProfile -> LoadingState()

                uiState.profile == null -> EmptyState(
                    icon = Icons.Outlined.PersonSearch,
                    title = "Perfil no disponible",
                    body = "El aula virtual no comparte el perfil de esta persona contigo."
                )

                else -> {
                    val profile = uiState.profile!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // Scrolls: a student with twelve courses makes this
                            // taller than any phone.
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = TienTheme.spacing.gutter,
                                vertical = TienTheme.spacing.base
                            )
                    ) {
                        ProfileField("Correo", profile.email)
                        ProfileField("Departamento", profile.department)
                        ProfileField("Ciudad", profile.city)
                        ProfileField("País", profile.country)
                        ProfileField("Último acceso", profile.lastAccess)

                        // Their whole enrolment, not just the part that overlaps
                        // with yours — the profile page exposes it, and hiding
                        // the rest was the app being less informative than the
                        // page it reads.
                        val shared = profile.courses.filter { it.shared }
                        val others = profile.courses.filterNot { it.shared }

                        if (shared.isNotEmpty()) {
                            Spacer(Modifier.height(TienTheme.spacing.loose))
                            SectionEyebrow(
                                text = if (shared.size == 1) {
                                    "1 curso contigo"
                                } else {
                                    "${shared.size} cursos contigo"
                                }
                            )
                            Spacer(Modifier.height(TienTheme.spacing.snug))
                            shared.forEach { course -> CourseLine(course.fullName) }
                        }

                        if (others.isNotEmpty()) {
                            Spacer(Modifier.height(TienTheme.spacing.loose))
                            SectionEyebrow(text = "Otros cursos que lleva")
                            Spacer(Modifier.height(TienTheme.spacing.snug))
                            others.forEach { course ->
                                CourseLine(
                                    text = course.fullName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseLine(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier.padding(bottom = TienTheme.spacing.tight)
    )
}

/** Renders nothing when the field is empty — an empty row is not information. */
@Composable
private fun ProfileField(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Column(modifier = Modifier.padding(bottom = TienTheme.spacing.base)) {
        SectionEyebrow(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
