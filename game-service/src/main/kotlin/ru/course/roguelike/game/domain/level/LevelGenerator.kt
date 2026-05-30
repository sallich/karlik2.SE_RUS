package ru.course.roguelike.game.domain.level

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos

interface LevelGenerator {
    fun generate(seed: Long): GeneratedLevel
}

/**
 * Прямоугольная комната лабиринта. Координаты [x]/[y] — левый нижний угол,
 * [width]/[height] — размеры в тайлах. Используется генератором, декорированием
 * (колонны/лава) и проверкой проходимости.
 */
data class Room(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val isBoss: Boolean = false,
) {
    val center: GridPos get() = GridPos(x + width / 2, y + height / 2)
    val area: Int get() = width * height

    fun contains(pos: GridPos): Boolean =
        pos.x in x until (x + width) && pos.y in y until (y + height)
}

data class GeneratedLevel(
    val map: TileMap,
    val playerSpawn: GridPos,
    val rooms: List<Room> = emptyList(),
)
