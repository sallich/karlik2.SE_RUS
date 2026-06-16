package ru.course.roguelike.policy.planner

import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

/**
 * Grid BFS that matches FPS stand positions (circle vs walls/columns).
 * Plain [ru.course.roguelike.shared.engine.GridPathfinder] treats any FLOOR cell as walkable
 * even when the player capsule cannot fit beside a COLUMN.
 *
 * With [allowVertical] = true, COLUMN cells become traversable "elevated" nodes: the player jumps
 * up onto a column, runs across column tops and drops back to the floor. The collision sweep for
 * any step touching a column is evaluated at [ELEVATED_HEIGHT] (the column top), where columns no
 * longer block movement (see [WorldVertical.blocksMovementAt]). Walls still block at every height.
 * This matches the FPS jump (apex ≈ 0.63 > COLUMN_HEIGHT 0.45) that real play uses to cross columns.
 */
object PolicyFpsPathfinder {
    private val OFFSETS = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))

    /** Stand height on top of a column; at this height columns stop blocking movement. */
    private const val ELEVATED_HEIGHT = WorldVertical.COLUMN_HEIGHT

    fun path(
        map: TileMap,
        start: GridPos,
        goal: GridPos,
        allowDamaging: Boolean = false,
        allowVertical: Boolean = false,
    ): List<GridPos>? {
        if (start == goal) return listOf(start)
        if (!isNavigable(map, start, allowDamaging, allowVertical) ||
            !isNavigable(map, goal, allowDamaging, allowVertical)
        ) {
            return null
        }

        val visited = HashSet<GridPos>()
        val parent = HashMap<GridPos, GridPos>()
        val queue = ArrayDeque<GridPos>()
        visited.add(start)
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == goal) return reconstruct(parent, start, goal)
            for (offset in OFFSETS) {
                val next = GridPos(cur.x + offset.x, cur.y + offset.y)
                if (next in visited || !canTraverse(map, cur, next, allowDamaging, allowVertical)) continue
                visited.add(next)
                parent[next] = cur
                queue.addLast(next)
            }
        }
        return null
    }

    fun nearestReachable(
        map: TileMap,
        start: GridPos,
        goals: List<GridPos>,
        allowDamaging: Boolean = false,
        allowVertical: Boolean = false,
    ): GridPos? {
        for (goal in goals) {
            if (path(map, start, goal, allowDamaging, allowVertical) != null) return goal
        }
        return null
    }

    fun navigableNeighbors(
        map: TileMap,
        cell: GridPos,
        allowDamaging: Boolean = false,
        allowVertical: Boolean = false,
    ): List<GridPos> =
        OFFSETS.map { GridPos(cell.x + it.x, cell.y + it.y) }
            .filter { canTraverse(map, cell, it, allowDamaging, allowVertical) }

    fun canTraverse(
        map: TileMap,
        from: GridPos,
        to: GridPos,
        allowDamaging: Boolean = false,
        allowVertical: Boolean = false,
    ): Boolean {
        if (!isNavigable(map, from, allowDamaging, allowVertical) ||
            !isNavigable(map, to, allowDamaging, allowVertical)
        ) {
            return false
        }
        val fx = from.x + 0.5f
        val fy = from.y + 0.5f
        val tx = to.x + 0.5f
        val ty = to.y + 0.5f
        // A step onto/off/between columns happens above the column top, so evaluate it elevated.
        val elevated = allowVertical && (isColumn(map, from) || isColumn(map, to))
        val height = if (elevated) ELEVATED_HEIGHT else 0f
        val radius = FpsConstants.PLAYER_RADIUS + FpsConstants.WALL_SKIN
        repeat(5) { step ->
            val t = step / 5f
            val x = fx + (tx - fx) * t
            val y = fy + (ty - fy) * t
            val circle = EntityCollision.Circle(x, y, radius)
            if (EntityCollision.overlapsMovement(map, circle, localHeight = height)) return false
        }
        return true
    }

    fun isNavigable(
        map: TileMap,
        pos: GridPos,
        allowDamaging: Boolean = false,
        allowVertical: Boolean = false,
    ): Boolean {
        val tile = map.get(pos) ?: return false
        val onColumn = allowVertical && tile == TileType.COLUMN
        val standable = tile == TileType.FLOOR ||
            tile == TileType.ELEVATOR ||
            tile == TileType.EXIT_GATE ||
            (allowDamaging && tile == TileType.LAVA) ||
            onColumn
        if (!standable) return false
        val x = pos.x + 0.5f
        val y = pos.y + 0.5f
        val height = if (onColumn) ELEVATED_HEIGHT else 0f
        val circle = EntityCollision.Circle(x, y, FpsConstants.PLAYER_RADIUS + FpsConstants.WALL_SKIN)
        return !EntityCollision.overlapsMovement(map, circle, localHeight = height)
    }

    private fun isColumn(map: TileMap, pos: GridPos): Boolean = map.get(pos) == TileType.COLUMN

    fun isCornerTrap(map: TileMap, cell: GridPos): Boolean =
        navigableNeighbors(map, cell, allowDamaging = true).size <= 2

    private fun reconstruct(parent: Map<GridPos, GridPos>, start: GridPos, goal: GridPos): List<GridPos>? {
        val path = mutableListOf(goal)
        var cur = goal
        while (cur != start) {
            cur = parent[cur] ?: return null
            path.add(0, cur)
        }
        return path
    }
}
