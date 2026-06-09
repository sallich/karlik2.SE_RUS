package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.cos
import kotlin.math.sin

/**
 * Миникарта для режима игрока (issue #14, клавиша M).
 *
 * В отличие от карты всей локации ([LocationMapOverlay], клавиша F4) показывает
 * только уже посещённые игроком клетки («туман войны»): по мере исследования
 * вокруг героя открываются комнаты. На открытых клетках отмечаются ещё не
 * собранные ключи и предметы, мобы, а также ворота выхода — чтобы игрок
 * помнил, где остался лут, и понимал, куда он уже ходил.
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
        mobs: List<MobSnapshot> = emptyList(),
        exitGate: GridPos? = null,
        doorMarkers: List<DoorMarkerSnapshot> = emptyList(),
    ) {
        if (visited.isEmpty()) return
        val layout = layout(screenWidth, screenHeight, map)
        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = MiniMapPalette.background
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        drawVisitedTiles(map, visited, layout)
        exitGate?.takeIf { visited.contains(it) }?.let { drawExitGate(it, layout) }
        drawDoorMarkers(doorMarkers, visited, layout)
        drawKeys(keyPickups, visited, layout)
        drawItems(items, visited, layout)
        drawMobs(mobs, visited, layout)
        pose?.let { drawPlayer(it, layout) }
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        shapeRenderer.end()
    }

    private fun drawVisitedTiles(map: TileMap, visited: Set<GridPos>, layout: Layout) {
        for (pos in visited) {
            val color = MiniMapPalette.cellColor(map.get(pos)) ?: continue
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
            if (!visited.contains(MiniMapPalette.cellOf(key.x, key.y))) continue
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
            if (!visited.contains(MiniMapPalette.cellOf(item.x, item.y))) continue
            shapeRenderer.color = MiniMapPalette.itemColor(item.kind)
            val px = layout.left + item.x * layout.cellPx
            val py = layout.bottom + item.y * layout.cellPx
            shapeRenderer.circle(px, py, (layout.cellPx * 0.3f).coerceAtLeast(2f), 10)
        }
    }

    private fun drawDoorMarkers(markers: List<DoorMarkerSnapshot>, visited: Set<GridPos>, layout: Layout) {
        for (marker in markers) {
            if (!visited.contains(MiniMapPalette.cellOf(marker.x, marker.y))) continue
            shapeRenderer.color = MiniMapPalette.markerColor(marker.kind)
            val px = layout.left + marker.x * layout.cellPx
            val py = layout.bottom + marker.y * layout.cellPx
            shapeRenderer.circle(px, py, (layout.cellPx * 0.28f).coerceAtLeast(2f), 8)
        }
    }

    private fun drawMobs(mobs: List<MobSnapshot>, visited: Set<GridPos>, layout: Layout) {
        mobs
            .asSequence()
            .filter { it.hp > 0 }
            .filter { visited.contains(MiniMapPalette.cellOf(it.x, it.y)) }
            .forEach { mob ->
                shapeRenderer.color = MiniMapPalette.mobColor(mob.kind)
                val px = layout.left + mob.x * layout.cellPx
                val py = layout.bottom + mob.y * layout.cellPx
                shapeRenderer.circle(px, py, (layout.cellPx * 0.35f).coerceAtLeast(2f), 10)
            }
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
}
