package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

class FpsMovementOnColumnTest {
    @Test
    fun `player can move while standing on column top`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[4] = TileType.COLUMN
        val map = TileMap(3, 3, tiles)
        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f, height = WorldVertical.COLUMN_HEIGHT)
        val moved = FpsMovementSystem.applyInput(
            map,
            pose,
            InputSyncRequest(forward = true, deltaMs = 100),
        )
        assertTrue(moved.x > pose.x, "expected forward movement on column top")
        assertEquals(WorldVertical.COLUMN_HEIGHT, moved.height, 0.01f)
    }
}
