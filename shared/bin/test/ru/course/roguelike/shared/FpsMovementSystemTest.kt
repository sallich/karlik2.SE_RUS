package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.hypot
class FpsMovementSystemTest {
    @Test
    fun `does not pass through wall in front`() {
        val map = TileMap(
            width = 3,
            height = 3,
            tiles = arrayOf(
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
                TileType.FLOOR, TileType.FLOOR, TileType.WALL,
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
            ),
        )
        val start = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val moved = FpsMovementSystem.applyInput(
            map,
            start,
            InputSyncRequest(forward = true, deltaMs = 500),
        )
        assertTrue(moved.x < 2f)
    }

    @Test
    fun `stops near wall at player radius`() {
        val map = TileMap(
            width = 4,
            height = 3,
            tiles = arrayOf(
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
                TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL,
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
            ),
        )
        val start = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val outcome = FpsMovementSystem.applyInputWithDebug(
            map,
            start,
            InputSyncRequest(forward = true, deltaMs = 800),
        )
        val maxX = 2f - FpsConstants.PLAYER_RADIUS - FpsConstants.WALL_SKIN
        assertTrue(outcome.pose.x > 1.6f)
        assertTrue(outcome.pose.x <= maxX + 0.02f)
        assertTrue(outcome.debug.blocked)
        assertTrue(outcome.debug.hitCells.isNotEmpty())
    }

    @Test
    fun `forward movement follows camera yaw over sub steps`() {
        val map = TileMap(10, 10, Array(100) { TileType.FLOOR })
        var pose = PlayerPose(5f, 5f, yaw = 0f)
        repeat(8) {
            pose = FpsMovementSystem.applyInput(
                map,
                pose,
                InputSyncRequest(forward = true, yawDelta = 0.12f, deltaMs = 16),
            )
        }
        assertTrue(pose.x > 5.1f)
        assertTrue(pose.y > 5.05f)
    }

    @Test
    fun `synced look batch matches per frame path`() {
        val map = TileMap(10, 10, Array(100) { TileType.FLOOR })
        val start = PlayerPose(5f, 5f, yaw = 0f)
        val stepped = (0 until 5).fold(start) { p, _ ->
            FpsMovementSystem.applyInput(
                map,
                p,
                InputSyncRequest(forward = true, yawDelta = 0.1f, deltaMs = 16),
            )
        }
        val batched = FpsMovementSystem.applyInput(
            map,
            start,
            InputSyncRequest(
                forward = true,
                yawDelta = 0.5f,
                deltaMs = 80,
                clientYaw = stepped.yaw,
                clientPitch = stepped.pitch,
            ),
        )
        assertTrue(hypot((batched.x - stepped.x).toDouble(), (batched.y - stepped.y).toDouble()) < 0.2)
    }

    @Test
    fun `batched yaw and forward matches sub step simulation`() {
        val map = TileMap(10, 10, Array(100) { TileType.FLOOR })
        val start = PlayerPose(5f, 5f, yaw = 0f)
        val stepped = (0 until 5).fold(start) { p, _ ->
            FpsMovementSystem.applyInput(
                map,
                p,
                InputSyncRequest(forward = true, yawDelta = 0.1f, deltaMs = 16),
            )
        }
        val batched = FpsMovementSystem.applyInput(
            map,
            start,
            InputSyncRequest(forward = true, yawDelta = 0.5f, deltaMs = 80),
        )
        assertTrue(hypot((batched.x - stepped.x).toDouble(), (batched.y - stepped.y).toDouble()) < 0.15)
    }

    @Test
    fun `slides along wall when blocked on one axis`() {
        val map = TileMap(
            width = 5,
            height = 3,
            tiles = arrayOf(
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL, TileType.WALL,
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.WALL,
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
            ),
        )
        val start = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val moved = FpsMovementSystem.applyInput(
            map,
            start,
            InputSyncRequest(forward = true, strafeLeft = true, deltaMs = 200),
        )
        assertTrue(moved.x > start.x + 0.05f)
    }

    @Test
    fun `diagonal input is normalized to max speed`() {
        val map = TileMap(3, 3, Array(9) { TileType.FLOOR })
        val start = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val moved = FpsMovementSystem.applyInput(
            map,
            start,
            InputSyncRequest(forward = true, strafeLeft = true, deltaMs = 100),
        )
        val dist = hypot((moved.x - start.x).toDouble(), (moved.y - start.y).toDouble()).toFloat()
        val maxStep = FpsConstants.MOVE_SPEED * 0.1f
        assertTrue(dist <= maxStep + 0.02f)
    }
}
