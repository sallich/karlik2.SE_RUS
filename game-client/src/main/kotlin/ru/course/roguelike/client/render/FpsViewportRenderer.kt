package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.render.Raycaster
import kotlin.math.sin

class FpsViewportRenderer(
    private val viewWidth: Int,
    private val viewHeight: Int,
) {
    private val pixmap = Pixmap(viewWidth, viewHeight, Pixmap.Format.RGB888)
    private lateinit var texture: Texture
    private var textureReady = false

    fun render(map: TileMap, pose: PlayerPose): Texture {
        val horizon = (viewHeight / 2f + sin(pose.pitch) * viewHeight * 0.45f)
            .coerceIn(0f, viewHeight.toFloat())
        val horizonInt = horizon.toInt()

        setRgb(0x2a3548)
        if (horizonInt > 0) {
            pixmap.fillRectangle(0, 0, viewWidth, horizonInt)
        }

        setRgb(0x3d4a3a)
        if (horizonInt < viewHeight) {
            pixmap.fillRectangle(0, horizonInt, viewWidth, viewHeight - horizonInt)
        }

        val columns = Raycaster.castColumns(map, pose, viewWidth, viewHeight, horizon)
        for (x in columns.indices) {
            val col = columns[x]
            val top = kotlin.math.floor(col.wallStart).toInt().coerceIn(0, viewHeight - 1)
            val bottom = kotlin.math.ceil(col.wallEnd).toInt().coerceIn(top + 1, viewHeight)
            setRgb(col.colorRgb)
            pixmap.fillRectangle(x, top, 1, bottom - top)
        }

        if (!textureReady) {
            texture = Texture(pixmap)
            textureReady = true
        } else {
            texture.draw(pixmap, 0, 0)
        }
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        return texture
    }

    fun dispose() {
        if (textureReady) {
            texture.dispose()
        }
        pixmap.dispose()
    }

    private fun setRgb(rgb: Int) {
        pixmap.setColor(
            ((rgb shr 16) and 0xFF) / 255f,
            ((rgb shr 8) and 0xFF) / 255f,
            (rgb and 0xFF) / 255f,
            1f,
        )
    }
}
