package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.GeneratedDungeon
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Двухуровневая лабиринтная локация (issue #3, стретч-цель).
 *
 * Генерирует два независимых лабиринта одинакового размера (нижний и верхний),
 * затем ставит несколько лифтов в общих проходимых клетках — на обоих уровнях
 * в одних и тех же координатах. Лифты проходимы, поэтому связность каждого
 * уровня сохраняется.
 */
object TwoLevelLabyrinthGenerator {
    /** Сдвиг seed для верхнего уровня — другой раскладки относительно нижнего. */
    private const val UPPER_SEED_SALT = 0x9E3779B9L

    /** Сколько пар лифтов разместить между уровнями. */
    private const val ELEVATOR_COUNT = 4

    fun generate(seed: Long): GeneratedDungeon {
        val ground = LabyrinthLevelGenerator.generate(seed)
        val upper = LabyrinthLevelGenerator.generate(seed xor UPPER_SEED_SALT)
        require(ground.map.width == upper.map.width && ground.map.height == upper.map.height) {
            "уровни лабиринта должны быть одного размера для общих координат лифтов"
        }
        return linkWithElevators(seed, ground, upper)
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
