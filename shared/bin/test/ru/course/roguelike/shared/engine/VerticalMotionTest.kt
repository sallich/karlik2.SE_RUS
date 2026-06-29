package ru.course.roguelike.shared.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

class VerticalMotionTest {
    private fun columnMap(): TileMap {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[4] = TileType.COLUMN
        return TileMap(3, 3, tiles)
    }

    @Test
    fun `standing inside column cell snaps up to walkable top`() {
        val map = columnMap()
        val state = VerticalMotion.tick(
            map,
            x = 1.5f,
            y = 1.5f,
            height = 0f,
            verticalVelocity = 0f,
            jumpRequested = false,
            deltaMs = 16,
        )
        assertEquals(WorldVertical.COLUMN_HEIGHT, state.height, 0.05f)
        assertEquals(0f, state.verticalVelocity, 0.05f)
    }

    @Test
    fun `landing on column top snaps to column height`() {
        val map = columnMap()
        var h = 0.5f
        var v = -1f
        repeat(30) {
            val state = VerticalMotion.tick(map, 1.5f, 1.5f, h, v, jumpRequested = false, deltaMs = 16)
            h = state.height
            v = state.verticalVelocity
        }
        assertEquals(WorldVertical.COLUMN_HEIGHT, h, 0.05f)
        assertEquals(0f, v, 0.05f)
    }

    @Test
    fun `walking off column ledge starts fall`() {
        val map = columnMap()
        var h = WorldVertical.COLUMN_HEIGHT
        var v = 0f
        repeat(40) {
            val state = VerticalMotion.tick(map, 0.5f, 0.5f, h, v, jumpRequested = false, deltaMs = 16)
            h = state.height
            v = state.verticalVelocity
        }
        assertTrue(h < WorldVertical.COLUMN_HEIGHT - 0.05f)
    }
}
