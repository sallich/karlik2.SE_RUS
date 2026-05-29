package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class TileMapTest {
    @Test
    fun `floor is walkable wall is not`() {
        val map = TileMap(2, 2, arrayOf(TileType.FLOOR, TileType.WALL, TileType.FLOOR, TileType.FLOOR))
        assertTrue(map.isWalkable(GridPos(0, 0)))
        assertFalse(map.isWalkable(GridPos(1, 0)))
    }
}
