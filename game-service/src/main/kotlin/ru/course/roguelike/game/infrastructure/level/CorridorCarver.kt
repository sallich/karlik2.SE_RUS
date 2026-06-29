package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Прокладывает узкие коридоры между комнатами лабиринта (issue #3).
 *
 * Топология строится как остовное дерево по сетке ячеек (рандомизированный обход
 * в глубину — даёт связность и развилки) плюс несколько дополнительных рёбер для
 * петель. Каждое ребро вырезается как Г-образный коридор шириной в один тайл
 * между центрами соседних комнат.
 */
object CorridorCarver {
    /** Вероятность добавить лишний коридор сверх остовного дерева (петли/развилки). */
    private const val EXTRA_CORRIDOR_CHANCE = 0.25

    fun connect(random: Random, tiles: Array<TileType>, width: Int, rooms: List<Room>, cols: Int, rows: Int) {
        val edges = LinkedHashSet<Pair<Int, Int>>()
        spanningTreeEdges(random, edges, cols, rows)
        extraLoopEdges(random, edges, cols, rows)
        for ((a, b) in edges) {
            carveCorridor(random, tiles, width, rooms[a].center, rooms[b].center)
        }
    }

    /** Рандомизированный DFS по сетке ячеек — связное остовное дерево с развилками. */
    private fun spanningTreeEdges(random: Random, edges: MutableSet<Pair<Int, Int>>, cols: Int, rows: Int) {
        val visited = BooleanArray(cols * rows)
        val stack = ArrayDeque<Int>()
        visited[0] = true
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val cur = stack.last()
            val next = gridNeighbors(cur, cols, rows).filterNot { visited[it] }.shuffled(random).firstOrNull()
            if (next == null) {
                stack.removeLast()
            } else {
                edges.add(edge(cur, next))
                visited[next] = true
                stack.addLast(next)
            }
        }
    }

    private fun extraLoopEdges(random: Random, edges: MutableSet<Pair<Int, Int>>, cols: Int, rows: Int) {
        for (cell in 0 until cols * rows) {
            for (neighbor in gridNeighbors(cell, cols, rows)) {
                val e = edge(cell, neighbor)
                if (e !in edges && random.nextDouble() < EXTRA_CORRIDOR_CHANCE) {
                    edges.add(e)
                }
            }
        }
    }

    /** Узкий (в один тайл) Г-образный коридор между центрами двух комнат. */
    private fun carveCorridor(random: Random, tiles: Array<TileType>, width: Int, a: GridPos, b: GridPos) {
        if (random.nextBoolean()) {
            carveHLine(tiles, width, a.x, b.x, a.y)
            carveVLine(tiles, width, a.y, b.y, b.x)
        } else {
            carveVLine(tiles, width, a.y, b.y, a.x)
            carveHLine(tiles, width, a.x, b.x, b.y)
        }
    }

    private fun carveHLine(tiles: Array<TileType>, width: Int, x0: Int, x1: Int, y: Int) {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) {
            tiles[y * width + x] = TileType.FLOOR
        }
    }

    private fun carveVLine(tiles: Array<TileType>, width: Int, y0: Int, y1: Int, x: Int) {
        for (y in minOf(y0, y1)..maxOf(y0, y1)) {
            tiles[y * width + x] = TileType.FLOOR
        }
    }

    private fun gridNeighbors(cell: Int, cols: Int, rows: Int): List<Int> {
        val gx = cell % cols
        val gy = cell / cols
        val result = ArrayList<Int>(4)
        if (gx > 0) result.add(cell - 1)
        if (gx < cols - 1) result.add(cell + 1)
        if (gy > 0) result.add(cell - cols)
        if (gy < rows - 1) result.add(cell + cols)
        return result
    }

    private fun edge(a: Int, b: Int): Pair<Int, Int> =
        if (a < b) a to b else b to a
}
