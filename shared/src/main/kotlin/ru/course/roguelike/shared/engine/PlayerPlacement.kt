package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import kotlin.math.floor
import kotlin.math.hypot

/** Безопасная постановка игрока на карту без пересечения со стенами и колоннами. */
object PlayerPlacement {
    private val NEIGHBORS = listOf(
        GridPos(1, 0),
        GridPos(-1, 0),
        GridPos(0, 1),
        GridPos(0, -1),
    )

    fun resolve(
        map: TileMap,
        preferredX: Float,
        preferredY: Float,
        localHeight: Float = 0f,
        searchBounds: ((GridPos) -> Boolean)? = null,
    ): Pair<Float, Float> {
        if (!blocked(map, preferredX, preferredY, localHeight)) {
            return preferredX to preferredY
        }
        nudgeNearby(map, preferredX, preferredY, localHeight)?.let { return it }

        val start = GridPos(floor(preferredX).toInt(), floor(preferredY).toInt())
        val queue = ArrayDeque<GridPos>()
        val visited = mutableSetOf<GridPos>()
        queue.add(start)

        var best: Pair<Float, Float>? = null
        var bestDist = Float.MAX_VALUE

        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell in visited) continue
            visited.add(cell)
            if (visited.size > 64) break

            if (searchBounds == null || searchBounds(cell)) {
                val tile = map.get(cell)
                if (tile != null && tile.walkable) {
                    val x = cell.x + 0.5f
                    val y = cell.y + 0.5f
                    if (!blocked(map, x, y, localHeight)) {
                        val dist = hypot((x - preferredX).toDouble(), (y - preferredY).toDouble()).toFloat()
                        if (dist < bestDist) {
                            bestDist = dist
                            best = x to y
                        }
                    }
                }
            }

            for (offset in NEIGHBORS) {
                val next = GridPos(cell.x + offset.x, cell.y + offset.y)
                if (next !in visited) queue.add(next)
            }
        }
        return best ?: (preferredX to preferredY)
    }

    private fun nudgeNearby(
        map: TileMap,
        x: Float,
        y: Float,
        localHeight: Float,
    ): Pair<Float, Float>? {
        val steps = floatArrayOf(0.05f, 0.1f, 0.18f, 0.28f, 0.4f)
        for (radius in steps) {
            for (i in 0 until 8) {
                val angle = (Math.PI * 2 * i / 8).toFloat()
                val nx = x + kotlin.math.cos(angle) * radius
                val ny = y + kotlin.math.sin(angle) * radius
                if (!blocked(map, nx, ny, localHeight)) return nx to ny
            }
        }
        return null
    }

    private fun blocked(map: TileMap, x: Float, y: Float, localHeight: Float): Boolean {
        val circle = EntityCollision.Circle(x, y, playerRadius())
        return EntityCollision.overlapsMovement(map, circle, localHeight)
    }

    fun playerRadius(): Float = FpsConstants.PLAYER_RADIUS + FpsConstants.WALL_SKIN
}
