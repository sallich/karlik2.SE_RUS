package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.GeneratedDungeon
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Двухуровневая локация (issue #3, стретч-цель).
 *
 * Это не два разных лабиринта, а один и тот же ярус, поднятый на второй уровень:
 * верхний ярус повторяет раскладку нижнего (та же локация, тот же seed). Лифт —
 * открытая платформа: наступив на неё, герой поднимается на 2-й ярус той же
 * локации в тех же координатах. Лифты ставятся в общих проходимых клетках на
 * обоих ярусах, поэтому связность каждого яруса сохраняется.
 */
object TwoLevelLabyrinthGenerator {
    /** Сколько пар лифтов разместить между уровнями. */
    private const val ELEVATOR_COUNT = 4

    fun generate(seed: Long): GeneratedDungeon {
        // Верхний ярус — та же локация, что и нижний (тот же layout), а не
        // отдельный лабиринт с другим seed: лифт лишь поднимает героя на 2-й ярус.
        val location = LabyrinthLevelGenerator.generate(seed)
        return linkWithElevators(seed, location, location)
    }

    private fun linkWithElevators(seed: Long, ground: GeneratedLevel, upper: GeneratedLevel): GeneratedDungeon {
        val width = ground.map.width
        val height = ground.map.height
        val groundTiles = ground.map.toFlatList().toMutableList()
        val upperTiles = upper.map.toFlatList().toMutableList()

        val groundSpawn = index(ground.playerSpawn, width)
        val upperSpawn = index(upper.playerSpawn, width)
        // Клетки, где на обоих уровнях пол и это не точка спавна.
        val floorCells = groundTiles.indices.filter { i ->
            i != groundSpawn && i != upperSpawn &&
                groundTiles[i] == TileType.FLOOR && upperTiles[i] == TileType.FLOOR
        }
        // Лифты ставим вплотную к колоннам: подняться имеет смысл там, где путь
        // загромождён колоннами. Если соседних с колоннами клеток не хватает,
        // добиваем остаток обычным полом, чтобы лифты всегда существовали.
        val (besideColumns, rest) = floorCells.partition { isAdjacentToColumn(it, groundTiles, width, height) }
        val random = Random(seed)
        val chosen = (besideColumns.shuffled(random) + rest.shuffled(random)).take(ELEVATOR_COUNT)
        for (i in chosen) {
            groundTiles[i] = TileType.ELEVATOR
            upperTiles[i] = TileType.ELEVATOR
        }
        // На верхнем ярусе над колоннами — проходимый пол (те же координаты, другой Z).
        for (i in upperTiles.indices) {
            if (upperTiles[i] == TileType.COLUMN) {
                upperTiles[i] = TileType.FLOOR
            }
        }

        return GeneratedDungeon(
            listOf(
                ground.copy(map = TileMap.fromFlat(width, height, groundTiles)),
                upper.copy(map = TileMap.fromFlat(width, height, upperTiles)),
            ),
        )
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
