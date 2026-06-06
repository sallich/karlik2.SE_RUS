package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

/**
 * Тайловая карта этажа. Общая для simulation (server) и отображения (client).
 */
class TileMap(
    val width: Int,
    val height: Int,
    private val tiles: Array<TileType>,
) {
    init {
        require(width > 0 && height > 0)
        require(tiles.size == width * height)
    }

    fun index(x: Int, y: Int): Int = y * width + x

    fun inBounds(pos: GridPos): Boolean =
        pos.x in 0 until width && pos.y in 0 until height

    fun get(pos: GridPos): TileType? =
        if (!inBounds(pos)) null else tiles[index(pos.x, pos.y)]

    fun isWalkable(pos: GridPos): Boolean = get(pos)?.walkable == true

    fun getTileAt(worldX: Float, worldY: Float): TileType? =
        get(GridPos(floor(worldX).toInt(), floor(worldY).toInt()))

    fun toFlatList(): List<TileType> = tiles.toList()

    companion object {
        fun fromFlat(width: Int, height: Int, flat: List<TileType>): TileMap =
            TileMap(width, height, flat.toTypedArray())
    }
}
