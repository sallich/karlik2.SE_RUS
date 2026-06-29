package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.GeneratedDungeon
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Лабиринт с лифтами (issue #3).
 *
 * Лифт — длинный вертикальный прыжок над колоннами на том же ярусе, без второго этажа.
 * Лифты ставятся вплотную к колоннам, чтобы подняться имело смысл.
 */
object TwoLevelLabyrinthGenerator {
    /** Сколько лифтов разместить на карте. */
    private const val ELEVATOR_COUNT = 4

    fun generate(seed: Long): GeneratedDungeon {
        val location = LabyrinthLevelGenerator.generate(seed)
        return GeneratedDungeon(listOf(placeElevators(seed, location)))
    }

    private fun placeElevators(seed: Long, level: GeneratedLevel): GeneratedLevel {
        val width = level.map.width
        val height = level.map.height
        val tiles = level.map.toFlatList().toMutableList()
        val spawnIndex = index(level.playerSpawn, width)

        val floorCells = tiles.indices.filter { i ->
            i != spawnIndex && tiles[i] == TileType.FLOOR
        }
        val (besideColumns, rest) = floorCells.partition { isAdjacentToColumn(it, tiles, width, height) }
        val random = Random(seed)
        val chosen = (besideColumns.shuffled(random) + rest.shuffled(random)).take(ELEVATOR_COUNT)
        for (i in chosen) {
            tiles[i] = TileType.ELEVATOR
        }
        return level.copy(map = TileMap.fromFlat(width, height, tiles))
    }

    /** Есть ли среди 8 соседей клетки [cell] хотя бы одна колонна. */
    private fun isAdjacentToColumn(cell: Int, tiles: List<TileType>, width: Int, height: Int): Boolean {
        val cx = cell % width
        val cy = cell / width
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until width && ny in 0 until height && tiles[ny * width + nx] == TileType.COLUMN) {
                    return true
                }
            }
        }
        return false
    }

    private fun index(pos: GridPos, width: Int): Int = pos.y * width + pos.x
}
