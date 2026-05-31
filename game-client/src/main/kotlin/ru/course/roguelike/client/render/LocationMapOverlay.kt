package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Карта всей локации для дебага (клавиша F4).
 *
 * В отличие от миникарты коллизий ([CollisionDebugOverlay], окно 7x7 вокруг
 * героя) рисует весь сгенерированный уровень целиком: стены, колонны, лаву,
 * лифты и пол — чтобы видеть, что и где сгенерировано и куда движется герой.
 *
 */
class LocationMapOverlay(
    private val shapeRenderer: ShapeRenderer,
) {
    fun render(
        screenWidth: Float,
        screenHeight: Float,
        map: TileMap,
        pose: PlayerPose?,
        mobs: List<MobSnapshot> = emptyList(),
    ) {
        val layout = layout(screenWidth, screenHeight, map)
        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = BACKGROUND
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        drawTiles(map, layout)
        drawMobs(mobs, layout)
        pose?.let { drawPlayer(it, layout) }
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        shapeRenderer.end()
    }

    private fun drawTiles(map: TileMap, layout: Layout) {
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val color = cellColor(map.get(GridPos(x, y))) ?: continue
                shapeRenderer.color = color
                shapeRenderer.rect(
                    layout.left + x * layout.cellPx,
                    layout.bottom + y * layout.cellPx,
                    layout.cellPx,
                    layout.cellPx,
                )
            }
        }
    }

    private fun drawMobs(mobs: List<MobSnapshot>, layout: Layout) {
        for (mob in mobs) {
            shapeRenderer.color = when (mob.kind) {
                MobKind.MELEE -> Color.GOLD
                MobKind.RANGED -> Color.SKY
            }
            val px = layout.left + mob.x * layout.cellPx
            val py = layout.bottom + mob.y * layout.cellPx
            shapeRenderer.circle(px, py, layout.cellPx * 0.45f, 12)
        }
    }

    private fun drawPlayer(pose: PlayerPose, layout: Layout) {
        val px = layout.left + pose.x * layout.cellPx
        val py = layout.bottom + pose.y * layout.cellPx
        val marker = (layout.cellPx * 1.5f).coerceAtLeast(3f)
        shapeRenderer.color = Color.SKY
        shapeRenderer.circle(px, py, marker, 12)
        val aimLen = marker * 2.2f
        shapeRenderer.rectLine(px, py, px + cos(pose.yaw) * aimLen, py + sin(pose.yaw) * aimLen, 1.5f)
    }

    private fun cellColor(tile: TileType?): Color? = when (tile) {
        TileType.FLOOR -> FLOOR
        TileType.WALL -> Color.DARK_GRAY
        TileType.COLUMN -> Color.GRAY
        TileType.LAVA -> Color.RED
        TileType.ELEVATOR -> Color.CYAN
        else -> null
    }

    private fun layout(screenWidth: Float, screenHeight: Float, map: TileMap): Layout {
        val pad = 12f
        val maxPanel = minOf(screenWidth, screenHeight) * 0.45f
        val cellPx = (maxPanel / maxOf(map.width, map.height)).coerceAtLeast(1f)
        val widthPx = cellPx * map.width
        val heightPx = cellPx * map.height
        return Layout(
            left = screenWidth - widthPx - pad,
            bottom = screenHeight - heightPx - pad,
            widthPx = widthPx,
            heightPx = heightPx,
            cellPx = cellPx,
        )
    }

    private data class Layout(
        val left: Float,
        val bottom: Float,
        val widthPx: Float,
        val heightPx: Float,
        val cellPx: Float,
    )

    private companion object {
        val BACKGROUND = Color(0f, 0f, 0f, 0.65f)
        val FLOOR = Color(0.16f, 0.18f, 0.22f, 0.9f)
    }
}
