package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.WorldVertical
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Круговые хитбоксы сущностей и лучевые проверки
 */
object EntityCollision {
    data class Circle(val x: Float, val y: Float, val radius: Float)

    fun circlesOverlap(a: Circle, b: Circle): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val reach = a.radius + b.radius
        return dx * dx + dy * dy <= reach * reach
    }

    fun distance(a: Circle, b: Circle): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    fun overlapsWall(map: TileMap, circle: Circle, floorLevel: Int = 0, worldZ: Float = 0f): Boolean {
        val minCellX = floor(circle.x - circle.radius).toInt()
        val maxCellX = floor(circle.x + circle.radius).toInt()
        val minCellY = floor(circle.y - circle.radius).toInt()
        val maxCellY = floor(circle.y + circle.radius).toInt()
        for (cy in minCellY..maxCellY) {
            for (cx in minCellX..maxCellX) {
                val tile = map.get(GridPos(cx, cy))
                if (tile == null || tile.walkable) continue
                if (worldZ > WorldVertical.tileTopWorldZ(floorLevel, tile) + 0.02f) continue
                if (circleOverlapsCell(circle.x, circle.y, circle.radius, cx, cy)) {
                    return true
                }
            }
        }
        return false
    }

    fun spheresOverlap3D(
        ax: Float,
        ay: Float,
        az: Float,
        ar: Float,
        bx: Float,
        by: Float,
        bz: Float,
        br: Float,
    ): Boolean {
        val dx = ax - bx
        val dy = ay - by
        val dz = az - bz
        val reach = ar + br
        return dx * dx + dy * dy + dz * dz <= reach * reach
    }

    fun playerHitCenterZ(pose: PlayerPose): Float = pose.height + WorldVertical.EYE_HEIGHT

    /**
     * Луч из [origin] по направлению [yaw] до [maxDistance].
     * Возвращает ближайшее пересечение с кругом или null.
     */
    fun raycastCircle(
        originX: Float,
        originY: Float,
        yaw: Float,
        maxDistance: Float,
        target: Circle,
    ): Float? {
        val dirX = cos(yaw)
        val dirY = sin(yaw)
        val ocX = originX - target.x
        val ocY = originY - target.y
        val a = dirX * dirX + dirY * dirY
        val b = 2f * (ocX * dirX + ocY * dirY)
        val c = ocX * ocX + ocY * ocY - target.radius * target.radius
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null

        val sqrtD = kotlin.math.sqrt(discriminant)
        val t1 = (-b - sqrtD) / (2f * a)
        val t2 = (-b + sqrtD) / (2f * a)
        val hit = listOf(t1, t2)
            .filter { it >= 0f && it <= maxDistance }
            .minOrNull()
        return hit
    }

    /** Луч до первой стены на карте (для ограничения атаки игрока). */
    fun raycastWall(map: TileMap, originX: Float, originY: Float, yaw: Float, maxDistance: Float): Float {
        val step = 0.05f
        var traveled = 0f
        while (traveled <= maxDistance) {
            val x = originX + cos(yaw) * traveled
            val y = originY + sin(yaw) * traveled
            if (overlapsWall(map, Circle(x, y, 0.01f))) {
                return traveled
            }
            traveled += step
        }
        return maxDistance
    }

    fun playerCircle(pose: PlayerPose): Circle =
        Circle(pose.x, pose.y, ru.course.roguelike.shared.model.FpsConstants.PLAYER_RADIUS)

    /** Блокировка движения с учётом высоты (колонны проходимы только с прыжка). */
    fun overlapsMovement(
        map: TileMap,
        circle: Circle,
        localHeight: Float,
        floorLevel: Int = 0,
    ): Boolean {
        val minCellX = floor(circle.x - circle.radius).toInt()
        val maxCellX = floor(circle.x + circle.radius).toInt()
        val minCellY = floor(circle.y - circle.radius).toInt()
        val maxCellY = floor(circle.y + circle.radius).toInt()
        for (cy in minCellY..maxCellY) {
            for (cx in minCellX..maxCellX) {
                val tile = map.get(GridPos(cx, cy)) ?: continue
                if (!WorldVertical.blocksMovementAt(floorLevel, tile, localHeight)) continue
                if (circleOverlapsCell(circle.x, circle.y, circle.radius, cx, cy)) {
                    return true
                }
            }
        }
        return false
    }

    fun moveWithWallSlide(
        map: TileMap,
        circle: Circle,
        dx: Float,
        dy: Float,
        localHeight: Float = 0f,
        floorLevel: Int = 0,
    ): Circle {
        if (dx == 0f && dy == 0f) return circle
        var nx = circle.x + dx
        var ny = circle.y + dy
        if (!overlapsMovement(map, circle.copy(x = nx, y = ny), localHeight, floorLevel)) {
            return circle.copy(x = nx, y = ny)
        }
        nx = circle.x + dx
        ny = circle.y
        if (!overlapsMovement(map, circle.copy(x = nx, y = ny), localHeight, floorLevel)) {
            return circle.copy(x = nx, y = ny)
        }
        nx = circle.x
        ny = circle.y + dy
        if (!overlapsMovement(map, circle.copy(x = nx, y = ny), localHeight, floorLevel)) {
            return circle.copy(x = nx, y = ny)
        }
        return circle
    }

    private fun circleOverlapsCell(px: Float, py: Float, radius: Float, cellX: Int, cellY: Int): Boolean {
        val closestX = px.coerceIn(cellX.toFloat(), cellX + 1f)
        val closestY = py.coerceIn(cellY.toFloat(), cellY + 1f)
        val diffX = px - closestX
        val diffY = py - closestY
        return diffX * diffX + diffY * diffY <= radius * radius
    }
}
