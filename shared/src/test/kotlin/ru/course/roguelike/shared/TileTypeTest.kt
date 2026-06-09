package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.model.wallHeight

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
    fun `locked door blocks movement but is an invisible see-through barrier`() {
        // Дверь — невидимый барьер коллизии: герой не проходит, но обзор не перекрыт
        // (сама дверь рисуется billboard-панелью). issue #24.
        assertFalse(TileType.DOOR_LOCKED.walkable)
        assertFalse(TileType.DOOR_LOCKED.blocksVision)
        assertFalse(TileType.DOOR_LOCKED.damaging)
        assertEquals(0f, TileType.DOOR_LOCKED.wallHeight())
    }

    @Test
    fun `only lava is damaging`() {
        val damaging = TileType.entries.filter { it.damaging }
        assertTrue(damaging == listOf(TileType.LAVA))
    }
}
