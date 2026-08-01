package com.tien.core.ui.feature.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import com.tien.core.ui.designsystem.theme.BoardPalette
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * The wall the papers are pinned to.
 *
 * **Why a shader and not a loop of dots.** Cork reads as cork because of its
 * grain, and grain means hundreds of specks. Drawing those per frame would cost
 * hundreds of draw calls every time the board pans. Instead the grain is baked
 * once into a small tileable bitmap and handed to the GPU as a repeating
 * shader: one draw call, regardless of how far the wall extends.
 *
 * **Why it moves with the camera.** A wall that stays put while its papers slide
 * over it reads as a scrolling list with a background image. Offsetting the
 * shader by the camera translation — modulo the tile size, so the maths stays
 * bounded no matter how far the user pans — makes the surface itself move.
 */
@Composable
fun CorkWall(
    camera: BoardCamera,
    palette: BoardPalette,
    modifier: Modifier = Modifier
) {
    // Rebuilt only when the theme changes, never per frame.
    val grain = remember(palette.wall, palette.wallGrainLight, palette.wallGrainDark) {
        ShaderBrush(
            ImageShader(
                buildCorkTile(palette),
                TileMode.Repeated,
                TileMode.Repeated
            )
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Base tone first: the tile carries grain over transparency, so the
        // wall colour has to exist underneath it.
        drawRect(palette.wall)

        // Modulo keeps the translation inside one tile. Without it, panning far
        // enough would push the shader origin into the range where float
        // precision starts to visibly quantise the texture.
        val period = TILE_SIZE_PX * camera.scale
        val shiftX = camera.offset.x.mod(period)
        val shiftY = camera.offset.y.mod(period)

        translate(left = shiftX - period, top = shiftY - period) {
            // Grown by two tiles so the shifted rect still covers every corner.
            drawRect(
                brush = grain,
                size = Size(size.width + period * 2, size.height + period * 2)
            )
        }

        // Vignette. A flat fill reads as a colour swatch; darkening the edges
        // suggests a surface lit from the front and gives the board depth.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, palette.wallShadow),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.75f
            )
        )
    }
}

/**
 * Bakes one tileable square of cork grain.
 *
 * The seed is fixed, so the wall looks the same on every launch — a texture that
 * reshuffles itself when the app restarts is the sort of detail that quietly
 * breaks the illusion of a physical object.
 */
private fun buildCorkTile(palette: BoardPalette): ImageBitmap {
    val bitmap = ImageBitmap(TILE_SIZE_PX.toInt(), TILE_SIZE_PX.toInt())
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val random = Random(CORK_SEED)
    val paint = Paint()

    repeat(SPECK_COUNT) {
        val x = random.nextFloat() * TILE_SIZE_PX
        val y = random.nextFloat() * TILE_SIZE_PX
        // Cork grain is granules of two tones, not noise of one.
        val light = random.nextBoolean()
        paint.color = if (light) palette.wallGrainLight else palette.wallGrainDark

        val radius = MIN_SPECK_RADIUS +
            random.nextFloat().absoluteValue * (MAX_SPECK_RADIUS - MIN_SPECK_RADIUS)
        canvas.drawCircle(Offset(x, y), radius, paint)

        // Specks near an edge are redrawn on the opposite side, so the tile
        // meets itself seamlessly instead of showing a visible grid.
        if (x < MAX_SPECK_RADIUS) {
            canvas.drawCircle(Offset(x + TILE_SIZE_PX, y), radius, paint)
        }
        if (y < MAX_SPECK_RADIUS) {
            canvas.drawCircle(Offset(x, y + TILE_SIZE_PX), radius, paint)
        }
        if (x < MAX_SPECK_RADIUS && y < MAX_SPECK_RADIUS) {
            canvas.drawCircle(Offset(x + TILE_SIZE_PX, y + TILE_SIZE_PX), radius, paint)
        }
    }

    return bitmap
}

private const val TILE_SIZE_PX = 128f
private const val SPECK_COUNT = 260
private const val MIN_SPECK_RADIUS = 0.8f
private const val MAX_SPECK_RADIUS = 3.2f

/** Fixed so the wall's grain is the same on every launch. */
private const val CORK_SEED = 0x7C0B
