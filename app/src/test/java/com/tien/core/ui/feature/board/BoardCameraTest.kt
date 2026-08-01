package com.tien.core.ui.feature.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The camera's coordinate maths.
 *
 * Worth pinning down because it is invisible when subtly wrong: a slightly-off
 * zoom anchor does not crash, it just makes the wall drift away from the
 * fingers, which reads as "the app feels bad" rather than as a bug.
 */
class BoardCameraTest {

    private fun camera(scale: Float = 1f, offset: Offset = Offset.Zero) =
        BoardCamera(initialOffset = offset, initialScale = scale).apply {
            viewportSize = Size(1000f, 2000f)
        }

    private fun assertOffsetNear(expected: Offset, actual: Offset, tolerance: Float = 0.01f) {
        assertTrue(
            "expected $expected but was $actual",
            abs(expected.x - actual.x) < tolerance && abs(expected.y - actual.y) < tolerance
        )
    }

    @Test
    fun `board and screen conversion round trips`() {
        val cam = camera(scale = 1.7f, offset = Offset(-120f, 340f))
        val boardPoint = Offset(512f, -88f)

        assertOffsetNear(boardPoint, cam.screenToBoard(cam.boardToScreen(boardPoint)))
    }

    /**
     * The point under the fingers must not move while pinching. This is the one
     * property that makes a zoom feel like the surface is being stretched rather
     * than sliding away.
     */
    @Test
    fun `zooming keeps the board point under the centroid fixed`() {
        val cam = camera(scale = 1f)
        val centroid = Offset(300f, 700f)
        val before = cam.screenToBoard(centroid)

        cam.transform(pan = Offset.Zero, zoom = 2.4f, centroid = centroid)

        assertOffsetNear(before, cam.screenToBoard(centroid))
    }

    @Test
    fun `panning moves the board by the drag amount`() {
        val cam = camera(scale = 1f)
        cam.transform(pan = Offset(50f, -30f), zoom = 1f, centroid = Offset(500f, 1000f))

        assertOffsetNear(Offset(50f, -30f), cam.offset)
    }

    @Test
    fun `scale is clamped at both ends`() {
        val zoomedOut = camera(scale = 1f)
        zoomedOut.transform(Offset.Zero, zoom = 0.001f, centroid = Offset.Zero)
        assertEquals(BoardCamera.MIN_SCALE, zoomedOut.scale, 0.001f)

        val zoomedIn = camera(scale = 1f)
        zoomedIn.transform(Offset.Zero, zoom = 1000f, centroid = Offset.Zero)
        assertEquals(BoardCamera.MAX_SCALE, zoomedIn.scale, 0.001f)
    }

    @Test
    fun `centering puts the point at the middle of the viewport`() {
        val cam = camera(scale = 1.5f)
        val target = Offset(-400f, 250f)

        cam.centerOn(target)

        assertOffsetNear(target, cam.viewportCenterInBoard())
    }

    /** Culling must not hide a paper that is only partly on screen. */
    @Test
    fun `visible rect is grown by the culling margin`() {
        val cam = camera(scale = 1f)
        val bounds = cam.visibleBoardRect()

        assertEquals(-BoardCamera.CULLING_MARGIN, bounds.left, 0.01f)
        assertEquals(-BoardCamera.CULLING_MARGIN, bounds.top, 0.01f)
        assertEquals(1000f + BoardCamera.CULLING_MARGIN, bounds.right, 0.01f)
        assertEquals(2000f + BoardCamera.CULLING_MARGIN, bounds.bottom, 0.01f)
    }

    /** Zooming out has to reveal more board, not less. */
    @Test
    fun `zooming out widens the visible rect`() {
        val cam = camera(scale = 1f)
        val before = cam.visibleBoardRect()

        cam.transform(Offset.Zero, zoom = 0.5f, centroid = Offset(500f, 1000f))
        val after = cam.visibleBoardRect()

        assertTrue(after.width > before.width)
        assertTrue(after.height > before.height)
    }
}
