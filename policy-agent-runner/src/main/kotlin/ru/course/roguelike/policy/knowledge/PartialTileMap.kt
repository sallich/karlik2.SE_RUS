package ru.course.roguelike.policy.knowledge

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/**
 * Navigation view: only [knownCells] expose real tiles; unknown cells are treated as walls.
 */
class PartialTileMap(
    private val full: TileMap,
    val knownCells: Set<String>,
    private val knownTiles: Map<String, TileType> = emptyMap(),
) {
    val width: Int get() = full.width
    val height: Int get() = full.height

    fun inBounds(pos: GridPos): Boolean = full.inBounds(pos)

    fun get(pos: GridPos): TileType? {
        if (!inBounds(pos)) return null
        val key = cellKey(pos)
        if (key !in knownCells) return TileType.WALL
        return knownTiles[key] ?: full.get(pos) ?: TileType.WALL
    }

    fun isWalkable(pos: GridPos): Boolean = get(pos)?.walkable == true

    fun toTileMap(): TileMap {
        val flat = Array(width * height) { TileType.WALL }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pos = GridPos(x, y)
                val tile = get(pos) ?: TileType.WALL
                flat[y * width + x] = tile
            }
        }
        return TileMap(width, height, flat)
    }

    companion object {
        fun cellKey(pos: GridPos): String = "${pos.x},${pos.y}"
        fun cellKey(x: Int, y: Int): String = "$x,$y"
    }
}
