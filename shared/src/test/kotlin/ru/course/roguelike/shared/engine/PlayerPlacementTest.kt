package ru.course.roguelike.shared.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class PlayerPlacementTest {
    @Test
    fun `resolves player out of a column`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.COLUMN
        val map = TileMap(3, 3, tiles)

        val (x, y) = PlayerPlacement.resolve(map, 1.5f, 1.5f)

        val circle = EntityCollision.Circle(x, y, PlayerPlacement.playerRadius())
        assertFalse(EntityCollision.overlapsMovement(map, circle, localHeight = 0f))
    }

    @Test
    fun `entry point near doorway avoids seal and column`() {
        val width = 7
        val height = 5
        val tiles = Array(width * height) { TileType.WALL }
        fun floor(x: Int, y: Int) {
            tiles[y * width + x] = TileType.FLOOR
        }
        for (y in 1..3) for (x in 1..5) floor(x, y)
        floor(0, 2)
        tiles[2 * width + 1] = TileType.ROOM_SEAL
        tiles[2 * width + 3] = TileType.COLUMN

        val map = TileMap(width, height, tiles)
        val (x, y) = PlayerPlacement.resolve(
            map = map,
            preferredX = 1.5f,
            preferredY = 2.5f,
            searchBounds = { it.x in 1..5 && it.y in 1..3 },
        )

        val circle = EntityCollision.Circle(x, y, PlayerPlacement.playerRadius())
        assertFalse(EntityCollision.overlapsMovement(map, circle, localHeight = 0f))
        assertFalse(map.get(GridPos(x.toInt(), y.toInt())) == TileType.COLUMN)
    }
}
