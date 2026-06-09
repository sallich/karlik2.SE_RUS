package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.model.wallHeight

class TileTypeTest {
    @Test
    fun `floor does not block movement or vision`() {
        assertTrue(TileType.FLOOR.walkable)
        assertFalse(TileType.FLOOR.blocksVision)
    }

    @Test
    fun `wall blocks movement and vision`() {
        assertFalse(TileType.WALL.walkable)
        assertTrue(TileType.WALL.blocksVision)
    }

    @Test
    fun `column blocks movement and vision like a wall but is its own type`() {
        assertFalse(TileType.COLUMN.walkable)
        assertTrue(TileType.COLUMN.blocksVision)
    }

    @Test
    fun `lava does not block vision`() {
        assertTrue(TileType.LAVA.walkable)
        assertFalse(TileType.LAVA.blocksVision)
    }

    @Test
    fun `room door is a full-height blocking entrance`() {
        assertFalse(TileType.ROOM_DOOR.walkable)
        assertTrue(TileType.ROOM_DOOR.blocksVision)
        assertEquals(WorldVertical.WALL_HEIGHT, TileType.ROOM_DOOR.wallHeight())
    }

    @Test
    fun `room seal is a full-height red barrier`() {
        assertFalse(TileType.ROOM_SEAL.walkable)
        assertTrue(TileType.ROOM_SEAL.blocksVision)
        assertEquals(WorldVertical.WALL_HEIGHT, TileType.ROOM_SEAL.wallHeight())
    }
}
