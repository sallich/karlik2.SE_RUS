package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Raycaster {
    data class Column(
        val wallStart: Float,
        val wallEnd: Float,
        val colorRgb: Int,
    )

    fun castColumns(
        map: TileMap,
        pose: PlayerPose,
        screenWidth: Int,
        screenHeight: Int,
        horizonY: Float,
        fovRadians: Float = FpsConstants.DEFAULT_FOV_RADIANS,
    ): Array<Column> {
        if (screenWidth <= 0) return emptyArray()

        val distances = FloatArray(screenWidth)
        val sides = IntArray(screenWidth)
        val colors = IntArray(screenWidth)

        val posX = pose.x
        val posY = pose.y
        val yaw = pose.yaw
        val halfFov = fovRadians / 2f
        val planeX = -sin(yaw) * tan(halfFov)
        val planeY = cos(yaw) * tan(halfFov)
        val dirX = cos(yaw)
        val dirY = sin(yaw)

        for (col in 0 until screenWidth) {
            val cameraX = (col + 0.5f) / screenWidth * 2f - 1f
            val rayDirX = dirX + planeX * cameraX
            val rayDirY = dirY + planeY * cameraX

            var mapX = floor(posX).toInt()
            var mapY = floor(posY).toInt()

            val deltaDistX = if (abs(rayDirX) < 1e-6f) 1e30f else abs(1f / rayDirX)
            val deltaDistY = if (abs(rayDirY) < 1e-6f) 1e30f else abs(1f / rayDirY)

            var sideDistX: Float
            var sideDistY: Float
            val stepX: Int
            val stepY: Int

            if (rayDirX < 0f) {
                stepX = -1
                sideDistX = (posX - mapX) * deltaDistX
            } else {
                stepX = 1
                sideDistX = (mapX + 1f - posX) * deltaDistX
            }
            if (rayDirY < 0f) {
                stepY = -1
                sideDistY = (posY - mapY) * deltaDistY
            } else {
                stepY = 1
                sideDistY = (mapY + 1f - posY) * deltaDistY
            }

            var hit = false
            var side = 0
            var outOfBounds = false

            while (!hit) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepY
                    side = 1
                }
                if (mapX < 0 || mapY < 0 || mapX >= map.width || mapY >= map.height) {
                    hit = true
                    outOfBounds = true
                } else if (map.get(GridPos(mapX, mapY)) == TileType.WALL) {
                    hit = true
                }
            }

            var perpWallDist = if (outOfBounds) {
                24f
            } else if (side == 0) {
                (mapX - posX + (1 - stepX) / 2f) / rayDirX
            } else {
                (mapY - posY + (1 - stepY) / 2f) / rayDirY
            }
            perpWallDist = abs(perpWallDist).coerceIn(FpsConstants.COLLISION_MIN_RAY_DIST, 64f)

            distances[col] = perpWallDist
            sides[col] = side
            val shade = (200 / (1f + perpWallDist * 0.28f)).toInt().coerceIn(50, 220)
            colors[col] = if (side == 0) {
                rgb(shade, shade * 2 / 3, shade / 2)
            } else {
                rgb(shade * 2 / 3, shade / 2, shade)
            }
        }

        smoothWallDistances(distances)

        return Array(screenWidth) { col ->
            val perpWallDist = distances[col]
            val lineHeight = screenHeight / perpWallDist
            val halfH = lineHeight / 2f
            val drawStart = (horizonY - halfH).coerceAtLeast(0f)
            val drawEnd = (horizonY + halfH).coerceAtMost(screenHeight.toFloat())
            Column(drawStart, drawEnd, colors[col])
        }
    }

    /**
     * Сглаживание глубины между соседними столбцами — убирает «лесенки» на дальних стенах.
     */
    private fun smoothWallDistances(distances: FloatArray) {
        if (distances.size < 3) return
        val scratch = distances.copyOf()
        for (col in 1 until distances.size - 1) {
            val d = distances[col]
            val weight = when {
                d > 8f -> 1f
                d > 4f -> 0.65f
                else -> 0.35f
            }
            if (weight < 0.01f) continue
            val smoothed = (scratch[col - 1] + scratch[col] * 2f + scratch[col + 1]) / 4f
            distances[col] = d + (smoothed - d) * weight
        }
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (r shl 16) or (g shl 8) or b
}
