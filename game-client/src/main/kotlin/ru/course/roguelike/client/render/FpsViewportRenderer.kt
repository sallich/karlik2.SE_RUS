package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
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

        paintSpecialFloor(map, pose, horizon, horizonInt)

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

    /**
     * Проекция пола для особых тайлов: для каждого пикселя ниже горизонта
     * вычисляем мировую точку пола и подсвечиваем лаву (оранжево-красный) и
     * лифты (бирюзовый). Стены рисуются поверх, перекрывая ближнюю часть,
     * поэтому подсветка видна только там, где тайл реально лежит на полу.
     */
    private fun paintSpecialFloor(map: TileMap, pose: PlayerPose, horizon: Float, horizonInt: Int) {
        val firstRow = horizonInt.coerceAtLeast(0)
        for (col in 0 until viewWidth) {
            val ray = Raycaster.rayDirection(pose, viewWidth, col)
            for (row in firstRow until viewHeight) {
                paintFloorPixel(map, pose, horizon, ray, col, row)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun paintFloorPixel(map: TileMap, pose: PlayerPose, horizon: Float, ray: FloatArray, col: Int, row: Int) {
        val dist = Raycaster.floorDistance(viewHeight, horizon, row)
        if (dist.isInfinite() || dist > MAX_SPECIAL_FLOOR_DISTANCE) return
        val floorX = pose.x + dist * ray[0]
        val floorY = pose.y + dist * ray[1]
        val glow = (255f / (1f + dist * 0.25f)).toInt().coerceIn(70, 255)
        val color = when (map.getTileAt(floorX, floorY)) {
            TileType.LAVA -> lavaRgba(glow)
            TileType.ELEVATOR -> elevatorRgba(glow)
            else -> return
        }
        pixmap.drawPixel(col, row, color)
    }

    /** Тёплый оттенок лавы (RGBA8888): яркий красный с убывающей по дистанции светимостью. */
    private fun lavaRgba(glow: Int): Int {
        val r = glow
        val g = (glow / 3).coerceAtMost(120)
        return (r shl 24) or (g shl 16) or (0 shl 8) or 0xFF
    }

    /** Холодный бирюзовый оттенок лифта (RGBA8888). */
    private fun elevatorRgba(glow: Int): Int {
        val g = glow
        val b = glow
        val r = (glow / 4).coerceAtMost(80)
        return (r shl 24) or (g shl 16) or (b shl 8) or 0xFF
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

    private companion object {
        /** Дальше этой дистанции особый пол не подсвечиваем (экономия и меньше шума на горизонте). */
        const val MAX_SPECIAL_FLOOR_DISTANCE = 28f
    }
}
