package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.LevelGenerator
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Простой процген для этапа движения: прямоугольный зал с стенами по периметру.
 * Позже заменяется шаблонами комнат из GDD.
 */
object TestLevelGenerator : LevelGenerator {
    const val DEFAULT_WIDTH = 20
    const val DEFAULT_HEIGHT = 20

    override fun generate(seed: Long): GeneratedLevel =
        generate(seed, DEFAULT_WIDTH, DEFAULT_HEIGHT)

    fun generate(seed: Long, width: Int, height: Int): GeneratedLevel {
        val random = Random(seed)
        val tiles = Array(width * height) { TileType.FLOOR }
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (isPerimeterWall(x, y, width, height)) {
                    tiles[y * width + x] = TileType.WALL
                }
            }
        }
        scatterWalls(random, tiles, width, height, count = 8)
        val spawn = findSpawn(tiles, width, height)
        return GeneratedLevel(TileMap(width, height, tiles), spawn)
    }

    private fun isPerimeterWall(x: Int, y: Int, width: Int, height: Int): Boolean =
        x == 0 || y == 0 || x == width - 1 || y == height - 1

    private fun scatterWalls(random: Random, tiles: Array<TileType>, width: Int, height: Int, count: Int) {
        repeat(count) {
            val x = random.nextInt(2, width - 2)
            val y = random.nextInt(2, height - 2)
            tiles[y * width + x] = TileType.WALL
        }
    }

    private fun findSpawn(tiles: Array<TileType>, width: Int, height: Int): GridPos {
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (tiles[y * width + x] == TileType.FLOOR) {
                    return GridPos(x, y)
                }
            }
        }
        return GridPos(1, 1)
    }
}
