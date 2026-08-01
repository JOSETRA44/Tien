package com.tien.core.ui.feature.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Where the viewer is standing in front of the wall.
 *
 * The board is unbounded; the camera is the window onto it. It holds a
 * translation and a scale, and converts between the two coordinate spaces:
 *
 *  - **board space** — where papers live. Persisted, independent of screen size.
 *  - **screen space** — pixels under the finger.
 *
 * `@Stable` rather than a data class: these values change on every frame of a
 * pan, and the whole point is that they are read inside `graphicsLayer` and
 * `offset` lambdas, where a change invalidates only the draw or layout phase.
 * Making it an immutable value passed down as a parameter would recompose the
 * entire board on every pixel of movement.
 */
@Stable
class BoardCamera(
    initialOffset: Offset = Offset.Zero,
    initialScale: Float = 1f
) {
    /** Screen-space translation applied to the board. */
    var offset by mutableStateOf(initialOffset)
        private set

    /** Zoom factor. 1 means one board unit per density-independent pixel. */
    var scale by mutableFloatStateOf(initialScale)
        private set

    /** Size of the viewport, needed to centre things and to cull off-screen papers. */
    var viewportSize by mutableStateOf(Size.Zero)

    /**
     * Applies one frame of a pan/pinch gesture.
     *
     * [centroid] is the midpoint of the pointers, in screen space. Zooming about
     * the centroid rather than the origin is what makes a pinch feel like the
     * wall is being pushed and pulled under the fingers instead of sliding away
     * toward a corner.
     */
    fun transform(pan: Offset, zoom: Float, centroid: Offset) {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)

        // The board point under the centroid must stay under the centroid.
        val boardPoint = screenToBoard(centroid)
        scale = newScale
        offset = centroid - boardPoint * newScale + pan
    }

    fun setScale(newScale: Float, aroundScreenPoint: Offset) {
        transform(Offset.Zero, newScale / scale, aroundScreenPoint)
    }

    /** Moves the camera so [boardPoint] sits at the centre of the viewport. */
    fun centerOn(boardPoint: Offset) {
        if (viewportSize == Size.Zero) return
        offset = Offset(
            viewportSize.width / 2f - boardPoint.x * scale,
            viewportSize.height / 2f - boardPoint.y * scale
        )
    }

    fun screenToBoard(screen: Offset): Offset = (screen - offset) / scale

    fun boardToScreen(board: Offset): Offset = board * scale + offset

    /** Board-space point currently at the centre of the screen. */
    fun viewportCenterInBoard(): Offset =
        screenToBoard(Offset(viewportSize.width / 2f, viewportSize.height / 2f))

    /**
     * The slice of board space currently visible, grown by [marginBoardUnits].
     *
     * Used to skip papers that are off-screen. The margin keeps a note that is
     * partly outside the viewport from popping in only once its top-left corner
     * crosses the edge.
     */
    fun visibleBoardRect(marginBoardUnits: Float = CULLING_MARGIN): Rect {
        if (viewportSize == Size.Zero) return Rect.Zero
        val topLeft = screenToBoard(Offset.Zero)
        val bottomRight = screenToBoard(Offset(viewportSize.width, viewportSize.height))
        return Rect(
            left = topLeft.x - marginBoardUnits,
            top = topLeft.y - marginBoardUnits,
            right = bottomRight.x + marginBoardUnits,
            bottom = bottomRight.y + marginBoardUnits
        )
    }

    companion object {
        // Below ~0.3 a paper is an illegible speck; above 3 the grain of the
        // wall texture starts to show as blur.
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 3f

        /** One paper's worth of slack around the viewport. */
        const val CULLING_MARGIN = 240f

        /**
         * Survives rotation, so the user is looking at the same part of the
         * wall afterwards rather than being thrown back to the origin.
         */
        // listSaver erases the element type to Any in its return, so that is
        // what the declared type has to be.
        val Saver: Saver<BoardCamera, Any> = listSaver<BoardCamera, Float>(
            save = { camera -> listOf(camera.offset.x, camera.offset.y, camera.scale) },
            restore = { saved ->
                BoardCamera(
                    initialOffset = Offset(saved[0], saved[1]),
                    initialScale = saved[2]
                )
            }
        )
    }
}
