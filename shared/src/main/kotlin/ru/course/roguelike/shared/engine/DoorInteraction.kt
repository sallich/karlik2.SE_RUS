package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InteractionConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Поиск двери комнаты для взаимодействия (E) — сервер и клиентские подсказки. */
object DoorInteraction {
    fun findInteractable(map: TileMap, pose: PlayerPose): GridPos? =
        findInView(map, pose) ?: findAdjacent(map, pose)

    /** Луч взгляда — приоритетный способ нажать E на дверь. */
    fun findInView(map: TileMap, pose: PlayerPose): GridPos? {
        val step = 0.12f
        var dist = 0.35f
        while (dist <= InteractionConstants.DOOR_INTERACT_RADIUS) {
            val x = pose.x + cos(pose.yaw) * dist
            val y = pose.y + sin(pose.yaw) * dist
            val cell = GridPos(floor(x).toInt(), floor(y).toInt())
            if (map.get(cell) == TileType.ROOM_DOOR) return cell
            dist += step
        }
        return null
    }

    /** Соседняя клетка с дверью в радиусе досягаемости. */
    fun findAdjacent(map: TileMap, pose: PlayerPose): GridPos? {
        val px = floor(pose.x).toInt()
        val py = floor(pose.y).toInt()
        var best: GridPos? = null
        var bestDist = InteractionConstants.DOOR_INTERACT_RADIUS
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val cell = GridPos(px + dx, py + dy)
                if (map.get(cell) != TileType.ROOM_DOOR) continue
                val dist = hypot(
                    (cell.x + 0.5f - pose.x).toDouble(),
                    (cell.y + 0.5f - pose.y).toDouble(),
                ).toFloat()
                if (dist <= bestDist) {
                    bestDist = dist
                    best = cell
                }
            }
        }
        val under = GridPos(px, py)
        if (map.get(under) == TileType.ROOM_DOOR) return under
        return best
    }
}
