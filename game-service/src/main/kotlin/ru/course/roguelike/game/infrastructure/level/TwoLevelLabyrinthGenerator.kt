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
        val candidates = groundTiles.indices.filter { i ->
            i != groundSpawn && i != upperSpawn &&
                groundTiles[i] == TileType.FLOOR && upperTiles[i] == TileType.FLOOR
        }
        val chosen = candidates.shuffled(Random(seed)).take(ELEVATOR_COUNT)
        for (i in chosen) {
            groundTiles[i] = TileType.ELEVATOR
            upperTiles[i] = TileType.ELEVATOR
        }

        return GeneratedDungeon(
            listOf(
                ground.copy(map = TileMap.fromFlat(width, height, groundTiles)),
                upper.copy(map = TileMap.fromFlat(width, height, upperTiles)),
            ),
        )
    }

    private fun index(pos: GridPos, width: Int): Int = pos.y * width + pos.x
}
