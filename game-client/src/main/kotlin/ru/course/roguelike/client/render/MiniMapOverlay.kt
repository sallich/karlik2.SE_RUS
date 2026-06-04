package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Миникарта для режима игрока (issue #14, клавиша M).
 *
 * В отличие от карты всей локации ([LocationMapOverlay], клавиша F4) показывает
 * только уже посещённые игроком клетки («туман войны»): по мере исследования
 * вокруг героя открываются комнаты. На открытых клетках отмечаются ещё не
 * собранные ключи и предметы, а также ворота выхода — чтобы игрок помнил, где
 * остался лут, и понимал, куда он уже ходил.
 */
class MiniMapOverlay(
    private val shapeRenderer: ShapeRenderer,
) {
    @Suppress("LongParameterList")
    fun render(
        screenWidth: Float,
        screenHeight: Float,
        map: TileMap,
        visited: Set<GridPos>,
        pose: PlayerPose?,
        keyPickups: List<KeySnapshot> = emptyList(),
        items: List<ItemSnapshot> = emptyList(),
        exitGate: GridPos? = null,
    ) {
        if (visited.isEmpty()) return
        val layout = layout(screenWidth, screenHeight, map)
        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = BACKGROUND
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        drawVisitedTiles(map, visited, layout)
        exitGate?.takeIf { visited.contains(it) }?.let { drawExitGate(it, layout) }
        drawKeys(keyPickups, visited, layout)
        drawItems(items, visited, layout)
        pose?.let { drawPlayer(it, layout) }
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        shapeRenderer.end()
    }

    private fun drawVisitedTiles(map: TileMap, visited: Set<GridPos>, layout: Layout) {
        for (pos in visited) {
            val color = cellColor(map.get(pos)) ?: continue
            shapeRenderer.color = color
            shapeRenderer.rect(
                layout.left + pos.x * layout.cellPx,
                layout.bottom + pos.y * layout.cellPx,
                layout.cellPx,
                layout.cellPx,
            )
        }
    }

    private fun drawKeys(keys: List<KeySnapshot>, visited: Set<GridPos>, layout: Layout) {
        for (key in keys) {
            if (!visited.contains(cellOf(key.x, key.y))) continue
            shapeRenderer.color = Color.GOLD
            val px = layout.left + key.x * layout.cellPx
            val py = layout.bottom + key.y * layout.cellPx
            shapeRenderer.rect(
                px - layout.cellPx * 0.2f,
                py - layout.cellPx * 0.2f,
                layout.cellPx * 0.4f,
                layout.cellPx * 0.4f,
            )
        }
    }

    private fun drawItems(items: List<ItemSnapshot>, visited: Set<GridPos>, layout: Layout) {
        for (item in items) {
            if (!visited.contains(cellOf(item.x, item.y))) continue
            shapeRenderer.color = itemColor(item.kind)
            val px = layout.left + item.x * layout.cellPx
            val py = layout.bottom + item.y * layout.cellPx
            shapeRenderer.circle(px, py, (layout.cellPx * 0.3f).coerceAtLeast(2f), 10)
        }
    }

    private fun itemColor(kind: ItemKind): Color = when (kind) {
        ItemKind.HEALTH -> Color.SCARLET
        ItemKind.EXPERIENCE -> Color.LIME
        ItemKind.WEAPON -> Color.LIGHT_GRAY
        ItemKind.AMMO -> Color.ORANGE
    }

    private fun drawExitGate(gate: GridPos, layout: Layout) {
        shapeRenderer.color = Color.GREEN
        val px = layout.left + gate.x * layout.cellPx
        val py = layout.bottom + gate.y * layout.cellPx
        shapeRenderer.rect(px, py, layout.cellPx, layout.cellPx)
    }

    private fun drawPlayer(pose: PlayerPose, layout: Layout) {
        val px = layout.left + pose.x * layout.cellPx
        val py = layout.bottom + pose.y * layout.cellPx
        val marker = (layout.cellPx * 1.2f).coerceAtLeast(3f)
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
        TileType.EXIT_GATE -> Color(0.2f, 0.85f, 0.35f, 1f)
        else -> null
    }

    private fun cellOf(worldX: Float, worldY: Float): GridPos =
        GridPos(floor(worldX).toInt(), floor(worldY).toInt())

    private fun layout(screenWidth: Float, screenHeight: Float, map: TileMap): Layout {
        val pad = 12f
        val maxPanel = minOf(screenWidth, screenHeight) * 0.3f
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
        val BACKGROUND = Color(0f, 0f, 0f, 0.6f)
        val FLOOR = Color(0.16f, 0.18f, 0.22f, 0.9f)
    }
}
