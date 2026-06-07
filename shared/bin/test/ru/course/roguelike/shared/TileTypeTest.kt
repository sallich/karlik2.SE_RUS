package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.TileType

class TileTypeTest {
    @Test
    fun `floor is walkable, transparent and harmless`() {
        assertTrue(TileType.FLOOR.walkable)
        assertFalse(TileType.FLOOR.blocksVision)
        assertFalse(TileType.FLOOR.damaging)
    }

    @Test
    fun `wall blocks movement and vision`() {
        assertFalse(TileType.WALL.walkable)
        assertTrue(TileType.WALL.blocksVision)
        assertFalse(TileType.WALL.damaging)
    }

    @Test
    fun `column blocks movement and vision like a wall but is its own type`() {
        assertFalse(TileType.COLUMN.walkable)
        assertTrue(TileType.COLUMN.blocksVision)
        assertFalse(TileType.COLUMN.damaging)
    }

    @Test
    fun `lava is walkable and transparent but damaging`() {
        assertTrue(TileType.LAVA.walkable)
        assertFalse(TileType.LAVA.blocksVision)
        assertTrue(TileType.LAVA.damaging)
    }

    @Test
    fun `elevator is walkable, transparent and harmless`() {
        assertTrue(TileType.ELEVATOR.walkable)
        assertFalse(TileType.ELEVATOR.blocksVision)
        assertFalse(TileType.ELEVATOR.damaging)
    }

    @Test
    fun `exit gate is walkable and visible`() {
        assertTrue(TileType.EXIT_GATE.walkable)
        assertFalse(TileType.EXIT_GATE.blocksVision)
        assertFalse(TileType.EXIT_GATE.damaging)
    }

    @Test
    fun `only lava is damaging`() {
        val damaging = TileType.entries.filter { it.damaging }
        assertTrue(damaging == listOf(TileType.LAVA))
    }
}
