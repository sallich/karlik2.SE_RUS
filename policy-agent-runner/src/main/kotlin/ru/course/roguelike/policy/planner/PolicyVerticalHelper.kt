package ru.course.roguelike.policy.planner

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

object PolicyVerticalHelper {
    fun onElevatorTile(map: TileMap, pose: PlayerPose): Boolean =
        map.get(GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())) == TileType.ELEVATOR

    fun columnBlocksToward(map: TileMap, pose: PlayerPose, target: GridPos): Boolean {
        if (pose.height >= WorldVertical.COLUMN_HEIGHT - 0.05f) return false
        val tx = target.x + 0.5f
        val ty = target.y + 0.5f
        val dx = tx - pose.x
        val dy = ty - pose.y
        if (hypot(dx.toDouble(), dy.toDouble()) < 0.12) return false
        repeat(4) { step ->
            val t = (step + 1) / 4f
            val probe = GridPos(
                floor(pose.x + dx * t).toInt(),
                floor(pose.y + dy * t).toInt(),
            )
            if (map.get(probe) == TileType.COLUMN) return true
        }
        val aheadX = floor(pose.x + cos(pose.yaw.toDouble()) * 0.55).toInt()
        val aheadY = floor(pose.y + sin(pose.yaw.toDouble()) * 0.55).toInt()
        return map.get(GridPos(aheadX, aheadY)) == TileType.COLUMN
    }

    fun shouldJumpForMove(map: TileMap, pose: PlayerPose, target: GridPos): Boolean =
        onElevatorTile(map, pose) || columnBlocksToward(map, pose, target)
}
