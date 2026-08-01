package com.tien.core.ui.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tien.core.ui.designsystem.component.ErrorState
import com.tien.core.ui.designsystem.component.LoadingState
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.core.ui.designsystem.theme.boardPalette
import kotlinx.coroutines.launch

/**
 * The wall.
 *
 * ### Gesture model
 * Deliberately mirrors what hands do at a physical board:
 *  - **Drag anywhere** moves your view along the wall. Nothing on a real board
 *    moves just because you brushed it.
 *  - **Pinch** steps back from it or leans in.
 *  - **Long press then drag** takes a paper off the wall and moves it. Picking
 *    something up is a decision, so it takes a deliberate press.
 *  - **Tap a paper** selects it and reveals its actions.
 *  - **Double-tap the wall** pins a new paper right there.
 *
 * Because a plain drag on a paper is *not* consumed by it, that gesture falls
 * through to the wall and pans — which is why panning still works when your
 * finger happens to land on a note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    viewModel: BoardViewModel,
    snackbarHostState: SnackbarHostState,
    showCreate: Boolean,
    onCreateHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = boardPalette
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val camera = rememberSaveable(saver = BoardCamera.Saver) { BoardCamera() }

    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingDraft by rememberSaveable { mutableStateOf("") }

    // The FAB lives in the app scaffold, so creation arrives as a flag.
    LaunchedEffect(showCreate) {
        if (showCreate) {
            val center = camera.viewportCenterInBoard()
            viewModel.onPinNote(center.x, center.y)
            onCreateHandled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BoardEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)

                is BoardEvent.EditNote -> {
                    editingNoteId = event.noteId
                    editingDraft = ""
                }

                is BoardEvent.NoteRemoved -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Papel quitado",
                        actionLabel = "Deshacer",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onUndoRemove()
                    }
                }
            }
        }
    }

    // Viewport culling.
    //
    // `derivedStateOf` is what makes this affordable: the filter re-runs on
    // every camera change, but recomposition only happens when the *result*
    // changes — that is, when a paper actually enters or leaves the screen.
    // Reading camera.offset directly in composition would instead recompose the
    // whole board on every frame of a pan.
    val visibleNotes by remember(uiState.notes) {
        derivedStateOf {
            val bounds = camera.visibleBoardRect()
            if (bounds == androidx.compose.ui.geometry.Rect.Zero) {
                uiState.notes
            } else {
                uiState.notes.filter { note ->
                    note.x + note.width >= bounds.left &&
                        note.x <= bounds.right &&
                        note.y + note.height >= bounds.top &&
                        note.y <= bounds.bottom
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.failure != null -> {
                ErrorState(
                    title = uiState.failure!!.title,
                    body = uiState.failure!!.body,
                    onRetry = viewModel::onRetry
                )
                return@Box
            }

            uiState.isLoading -> {
                LoadingState()
                return@Box
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    camera.viewportSize = Size(it.width.toFloat(), it.height.toFloat())
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        camera.transform(pan, zoom, centroid)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { viewModel.onDeselect() },
                        onDoubleTap = { screenPoint ->
                            val boardPoint = camera.screenToBoard(screenPoint)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onPinNote(boardPoint.x, boardPoint.y)
                        }
                    )
                }
        ) {
            CorkWall(camera = camera, palette = palette)

            // One layer for the whole board. Panning and zooming become a GPU
            // transform on this single layer instead of a relayout of every
            // paper on it — the reason the wall stays smooth with a lot pinned.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = camera.offset.x
                        translationY = camera.offset.y
                        scaleX = camera.scale
                        scaleY = camera.scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        clip = false
                    }
            ) {
                // Threads sit beneath every sheet, so string always passes
                // behind the papers it ties.
                BoardThreads(
                    links = uiState.links,
                    notesById = uiState.notesById,
                    palette = palette
                )

                visibleNotes.forEach { note ->
                    key(note.id) {
                        PaperNote(
                            note = note,
                            palette = palette,
                            isSelected = note.id == uiState.selectedNoteId,
                            isLinkSource = note.id == uiState.linkSourceId,
                            onTap = { viewModel.onNoteTapped(note.id) },
                            onPickUp = { viewModel.onNotePickedUp(note.id) },
                            onDrop = { x, y -> viewModel.onNoteMoved(note.id, x, y) }
                        )
                    }
                }
            }
        }

        if (uiState.isEmpty) {
            EmptyWallHint(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = uiState.isLinking,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LinkingBanner(onCancel = viewModel::onCancelLink)
        }

        AnimatedVisibility(
            visible = uiState.selectedNote != null && !uiState.isLinking,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selected = uiState.selectedNote
            PaperToolbar(
                onEdit = {
                    selected?.let {
                        editingNoteId = it.id
                        editingDraft = it.text
                    }
                },
                onLink = { selected?.let { viewModel.onStartLink(it.id) } },
                onColor = { selected?.let { viewModel.onCycleColor(it.id) } },
                onRemove = { selected?.let { viewModel.onRemoveNote(it.id) } }
            )
        }
    }

    val editingId = editingNoteId
    if (editingId != null) {
        val note = uiState.notesById[editingId]
        PaperEditorSheet(
            sheetState = editorSheetState,
            initialText = note?.text ?: editingDraft,
            onDismiss = {
                scope.launch { editorSheetState.hide() }
                    .invokeOnCompletion { editingNoteId = null }
            },
            onSave = { text -> viewModel.onEditText(editingId, text) },
            onRemove = {
                viewModel.onRemoveNote(editingId)
                editingNoteId = null
            }
        )
    }
}

@Composable
private fun EmptyWallHint(modifier: Modifier = Modifier) {
    val palette = boardPalette
    Column(
        modifier = modifier.padding(TienTheme.spacing.page),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.PushPin,
            contentDescription = null,
            tint = palette.pinHead.copy(alpha = 0.9f),
            modifier = Modifier.height(40.dp)
        )
        Spacer(Modifier.height(TienTheme.spacing.base))
        Text(
            text = "La pared está vacía",
            style = MaterialTheme.typography.titleLarge,
            color = palette.pinHead,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TienTheme.spacing.snug))
        Text(
            // Teaches the gesture instead of pointing at a button.
            text = "Toca dos veces la pared para clavar tu primera idea.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.pinHead.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PaperToolbar(
    onEdit: () -> Unit,
    onLink: () -> Unit,
    onColor: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(TienTheme.spacing.comfy)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TienTheme.spacing.snug),
            horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.tight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Escribir en el papel")
            }
            IconButton(onClick = onColor) {
                Icon(Icons.Outlined.Palette, contentDescription = "Cambiar de papel")
            }
            IconButton(onClick = onLink) {
                Icon(Icons.Outlined.Link, contentDescription = "Unir con otro papel")
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Quitar de la pared",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LinkingBanner(onCancel: () -> Unit) {
    val palette = boardPalette
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(TienTheme.spacing.comfy)
    ) {
        Row(
            modifier = Modifier.padding(
                start = TienTheme.spacing.comfy,
                end = TienTheme.spacing.snug
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TienTheme.spacing.snug)
        ) {
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .width(18.dp)
                    .background(palette.thread, RoundedCornerShape(2.dp))
            )
            Text(
                text = "Toca otro papel para unirlos",
                style = TienTextStyles.meta,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancelar")
            }
        }
    }
}
