package ru.course.roguelike.policy.planner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import kotlin.math.PI

class PolicyRoomExitPlannerTest {
    @Test
    fun `exit after combat yaw uses game_sync not compass act`() {
        val snapshot = roomSnapshot(yaw = (PI / 2).toFloat())
        val decision = PolicyRoomExitPlanner.navigateToExit("s1", snapshot)
        assertEquals("game_sync", decision.tool)
    }

    @Test
    fun `bounded flood fill stays inside mob room not corridor`() {
        val w = 11
        val h = 7
        val tiles = MutableList(w * h) { TileType.WALL }
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                tiles[y * w + x] = TileType.FLOOR
            }
        }
        for (y in 1 until h - 1) {
            tiles[y * w + 0] = TileType.FLOOR
        }
        val snapshot = GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 5.5f, y = 3.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
        )
        val region = PolicyRoomExitPlanner.captureRoomRegion(snapshot)
        assertTrue("5,3" in region)
        assertTrue("0,3" !in region, "corridor west of room should not be in frozen region")
    }

    @Test
    fun `detects enclosed room`() {
        val snapshot = roomSnapshot(yaw = 0f)
        assertTrue(PolicyRoomExitPlanner.isInsideEnclosedRoom(snapshot))
    }

    @Test
    fun `room with column does not treat column-adjacent cell as doorway`() {
        // 9x9 room, single exit corridor on the west wall, one column inside.
        val w = 9
        val h = 9
        val tiles = MutableList(w * h) { TileType.WALL }
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                tiles[y * w + x] = TileType.FLOOR
            }
        }
        // one exit corridor to the west at row 4
        tiles[4 * w + 0] = TileType.FLOOR
        // a column in the middle of the room
        tiles[4 * w + 5] = TileType.COLUMN
        val snapshot = GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 6.5f, y = 4.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 3,
        )
        // The only real exit goal is the west corridor cell (0,4); column must not create extras.
        // Build region = all interior floor cells (what a correct capture should yield).
        val map = TileMap.fromFlat(w, h, tiles)
        val region = buildSet {
            for (yy in 0 until h) for (xx in 0 until w) {
                if (map.get(GridPos(xx, yy)) == TileType.FLOOR) add(GridPos(xx, yy))
            }
        }
        val goals = PolicyRoomExitPlanner.findExitGoals(map, region, GridPos(6, 4), snapshot)
        assertTrue(goals.contains(GridPos(0, 4)), "west corridor must be an exit goal: $goals")
        // No exit goal should be a cell adjacent to the column inside the room.
        assertTrue(
            goals.none { it == GridPos(4, 4) || it == GridPos(6, 4) },
            "column-adjacent interior cells must not be exit goals: $goals",
        )
    }

    @Test
    fun `room exit can path across lava when it is the only way out`() {
        // Corridor west blocked by lava at (1,4); exit beyond.
        val w = 7
        val h = 7
        val tiles = MutableList(w * h) { TileType.WALL }
        for (y in 1 until h - 1) {
            for (x in 2 until w - 1) {
                tiles[y * w + x] = TileType.FLOOR
            }
        }
        tiles[3 * w + 1] = TileType.LAVA
        tiles[3 * w + 0] = TileType.FLOOR
        val snapshot = GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 4.5f, y = 3.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
        )
        val decision = PolicyRoomExitPlanner.navigateToExit("s1", snapshot)
        assertEquals("game_sync", decision.tool)
    }

    private fun roomSnapshot(yaw: Float): GameSnapshot {
        val w = 9
        val h = 9
        val tiles = MutableList(w * h) { TileType.WALL }
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                tiles[y * w + x] = TileType.FLOOR
            }
        }
        tiles[4 * w + 0] = TileType.FLOOR
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 4.5f, y = 4.5f, yaw = yaw, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 3,
        )
    }
}
