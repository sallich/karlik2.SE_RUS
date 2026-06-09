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
import ru.course.roguelike.shared.model.ItemKind
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
    @Suppress("LongParameterList")
    fun render(
        screenWidth: Float,
        screenHeight: Float,
        map: TileMap,
        pose: PlayerPose?,
        mobs: List<MobSnapshot> = emptyList(),
        keyPickups: List<KeySnapshot> = emptyList(),
        items: List<ItemSnapshot> = emptyList(),
        exitGate: GridPos? = null,
        doorMarkers: List<DoorMarkerSnapshot> = emptyList(),
    ) {
        val layout = layout(screenWidth, screenHeight, map)
        shapeRenderer.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, screenWidth, screenHeight)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = BACKGROUND
        shapeRenderer.rect(layout.left, layout.bottom, layout.widthPx, layout.heightPx)
        drawTiles(map, layout)
        exitGate?.let { drawExitGate(it, layout) }
        doorMarkers.forEach { marker ->
            shapeRenderer.color = marker.kind?.let(::itemColor) ?: Color.GOLD
            shapeRenderer.circle(
                layout.left + marker.x * layout.cellPx,
                layout.bottom + marker.y * layout.cellPx,
                layout.cellPx * 0.2f,
                8,
            )
        }
        drawKeys(keyPickups, layout)
        drawItems(items, layout)
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

    private fun drawKeys(keys: List<KeySnapshot>, layout: Layout) {
        for (key in keys) {
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

    private fun drawItems(items: List<ItemSnapshot>, layout: Layout) {
        for (item in items) {
            shapeRenderer.color = itemColor(item.kind)
            val px = layout.left + item.x * layout.cellPx
            val py = layout.bottom + item.y * layout.cellPx
            shapeRenderer.circle(px, py, layout.cellPx * 0.25f, 10)
        }
    }

    private fun itemColor(kind: ItemKind): Color = when (kind) {
        ItemKind.HEALTH -> Color.SCARLET
        ItemKind.EXPERIENCE -> Color.LIME
        ItemKind.WEAPON_PISTOL -> Color.SKY
        ItemKind.WEAPON_SHOTGUN -> Color.FIREBRICK
        ItemKind.AMMO_PISTOL -> Color.SKY
        ItemKind.AMMO_SHOTGUN -> Color.ORANGE
    }

    private fun drawExitGate(gate: GridPos, layout: Layout) {
        shapeRenderer.color = Color.GREEN
        val px = layout.left + gate.x * layout.cellPx
        val py = layout.bottom + gate.y * layout.cellPx
        shapeRenderer.rect(px, py, layout.cellPx, layout.cellPx)
    }

    private fun drawMobs(mobs: List<MobSnapshot>, layout: Layout) {
        for (mob in mobs) {
            shapeRenderer.color = when (mob.kind) {
                MobKind.MELEE -> Color.GOLD
                MobKind.RANGED -> Color.SKY
                MobKind.LLM_GUARD -> Color.MAGENTA
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
        TileType.EXIT_GATE -> Color(0.2f, 0.85f, 0.35f, 1f)
        TileType.DOOR_LOCKED -> Color(0.8f, 0.2f, 0.16f, 1f)
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
