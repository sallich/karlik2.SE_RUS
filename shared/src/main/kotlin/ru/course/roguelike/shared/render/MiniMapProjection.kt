package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.cos
import kotlin.math.sin

/**
 * Проекция мировых координат на миникарту, привязанную к игроку:
 */
object MiniMapProjection {
    data class MinimapPoint(
        /** Смещение вправо от игрока, в тайлах. */
        val right: Float,
        /** Смещение вперёд от игрока, в тайлах. */
        val forward: Float,
    )

    fun worldToMinimap(pose: PlayerPose, worldX: Float, worldY: Float): MinimapPoint {
        val relX = worldX - pose.x
        val relY = worldY - pose.y
        val cosYaw = cos(pose.yaw)
        val sinYaw = sin(pose.yaw)
        val forward = relX * cosYaw + relY * sinYaw
        val right = -relX * sinYaw + relY * cosYaw
        return MinimapPoint(right = right, forward = forward)
    }

    fun aimEnd(pose: PlayerPose, lengthTiles: Float = 0.45f): MinimapPoint =
        MinimapPoint(right = 0f, forward = lengthTiles)

    fun isVisible(point: MinimapPoint, radiusCells: Float): Boolean =
        kotlin.math.abs(point.right) <= radiusCells && kotlin.math.abs(point.forward) <= radiusCells

    fun toScreen(originX: Float, originY: Float, cellPx: Float, point: MinimapPoint): Pair<Float, Float> =
        originX + point.right * cellPx to originY + point.forward * cellPx
}
