package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class RoomDoorwaysTest {
    @Test
    fun `doorway is the perimeter cell opening into a corridor`() {
        val room = Room(1, 1, 3, 3)
        // Карта: комната 3x3 в углу + одноклеточный коридор, выходящий вправо из (3,2).
        val width = 6
        val height = 6
        val tiles = Array(width * height) { TileType.WALL }
        fun floor(x: Int, y: Int) {
            tiles[y * width + x] = TileType.FLOOR
        }
        for (y in 1..3) for (x in 1..3) floor(x, y)
        floor(4, 2) // коридорная клетка за пределами комнаты
        val map = TileMap(width, height, tiles)

        val doorways = RoomDoorways.of(map, room)

        assertEquals(listOf(GridPos(3, 2)), doorways)
    }

    @Test
    fun `isolated room has no doorways`() {
        val room = Room(1, 1, 3, 3)
        val width = 6
        val height = 6
        val tiles = Array(width * height) { TileType.WALL }
        for (y in 1..3) for (x in 1..3) tiles[y * width + x] = TileType.FLOOR
        val map = TileMap(width, height, tiles)

        assertTrue(RoomDoorways.of(map, room).isEmpty())
    }
}
