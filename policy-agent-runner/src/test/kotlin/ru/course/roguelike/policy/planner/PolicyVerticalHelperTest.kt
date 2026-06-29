package ru.course.roguelike.policy.planner

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class PolicyVerticalHelperTest {
    @Test
    fun `detects column blocking forward move`() {
        val w = 5
        val tiles = MutableList(w * w) { TileType.FLOOR }
        tiles[2 * w + 2] = TileType.COLUMN
        val map = TileMap.fromFlat(w, w, tiles)
        val pose = PlayerPose(1.5f, 2.5f, yaw = 0f, pitch = 0f)
        assertTrue(PolicyVerticalHelper.columnBlocksToward(map, pose, GridPos(2, 2)))
    }

    @Test
    fun `elevator tile requests jump on move`() {
        val w = 3
        val tiles = listOf(
            TileType.WALL, TileType.WALL, TileType.WALL,
            TileType.WALL, TileType.ELEVATOR, TileType.WALL,
            TileType.WALL, TileType.WALL, TileType.WALL,
        )
        val map = TileMap.fromFlat(w, w, tiles)
        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f, pitch = 0f)
        assertTrue(PolicyVerticalHelper.shouldJumpForMove(map, pose, GridPos(1, 2)))
    }
}
