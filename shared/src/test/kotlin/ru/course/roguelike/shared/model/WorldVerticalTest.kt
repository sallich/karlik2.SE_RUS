package ru.course.roguelike.shared.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldVerticalTest {
    @Test
    fun `jumping eye clears column collision and vision`() {
        assertTrue(WorldVertical.blocksMovementAt(0, TileType.COLUMN, localHeight = 0f))
        assertFalse(WorldVertical.blocksMovementAt(0, TileType.COLUMN, localHeight = WorldVertical.COLUMN_HEIGHT))
        assertTrue(WorldVertical.blocksVisionAt(0, TileType.COLUMN, eyeWorldZ = 0.35f))
        assertFalse(WorldVertical.blocksVisionAt(0, TileType.COLUMN, eyeWorldZ = 1.0f))
    }

    @Test
    fun `upper floor eye is one step higher`() {
        assertTrue(
            WorldVertical.eyeWorldZ(floorLevel = 1, localHeight = 0f) >
                WorldVertical.eyeWorldZ(floorLevel = 0, localHeight = 0f),
        )
    }
}
