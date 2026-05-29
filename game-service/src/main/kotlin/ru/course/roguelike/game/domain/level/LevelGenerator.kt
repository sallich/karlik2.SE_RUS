package ru.course.roguelike.game.domain.level

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos

interface LevelGenerator {
    fun generate(seed: Long): GeneratedLevel
}

data class GeneratedLevel(
    val map: TileMap,
    val playerSpawn: GridPos,
)
