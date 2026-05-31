package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

object GridPathfinder {
    private val OFFSETS = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))

    /** BFS по безопасному полу (FLOOR, не лава). Возвращает путь включая start и goal. */
    fun path(map: TileMap, start: GridPos, goal: GridPos): List<GridPos>? {
        if (start == goal) return listOf(start)
        if (!isWalkable(map, start) || !isWalkable(map, goal)) return null

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
                if (next in visited || !isWalkable(map, next)) continue
                visited.add(next)
                parent[next] = cur
                queue.addLast(next)
            }
        }
        return null
    }

    fun nearestReachable(map: TileMap, start: GridPos, goals: List<GridPos>): GridPos? {
        for (goal in goals.sortedBy { manhattan(start, it) }) {
            if (path(map, start, goal) != null) return goal
        }
        return null
    }

    private fun reconstruct(parent: Map<GridPos, GridPos>, start: GridPos, goal: GridPos): List<GridPos>? {
        val path = mutableListOf(goal)
        var cur = goal
        while (cur != start) {
            cur = parent[cur] ?: return null
            path.add(0, cur)
        }
        return path
    }

    private fun isWalkable(map: TileMap, pos: GridPos): Boolean =
        map.get(pos) == TileType.FLOOR || map.get(pos) == TileType.EXIT_GATE

    private fun manhattan(a: GridPos, b: GridPos): Int =
        kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)
}
