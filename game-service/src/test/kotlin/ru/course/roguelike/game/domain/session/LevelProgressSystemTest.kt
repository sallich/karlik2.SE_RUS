package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class LevelProgressSystemTest {
    @Test
    fun `interact on exit gate with all keys completes level`() {
        val level = LabyrinthLevelGenerator.generate(seed = 42L)
        val boss = level.rooms.single { it.isBoss }
        val (map, exitGate) = ExitGatePlacer.place(level, boss)
        val keys = KeySpawner.spawn(level, seed = 42L).onEach { it.collected = true }
        val session = GameSession(
            sessionId = "progress",
            seed = 42L,
            map = map,
            playerPose = PlayerPose(exitGate.x + 0.5f, exitGate.y + 0.5f, yaw = 0f),
            keyPickups = keys.toMutableList(),
            bossRoom = boss,
            exitGate = exitGate,
        )

        val events = LevelProgressSystem.apply(session, InputSyncRequest(interact = true))

        assertTrue(session.levelCompleted)
        assertTrue(events.any { it is GameEvent.LevelCompleted })
    }

    @Test
    fun `exit gate without all keys does not complete level`() {
        val level = LabyrinthLevelGenerator.generate(seed = 7L)
        val boss = level.rooms.single { it.isBoss }
        val (map, exitGate) = ExitGatePlacer.place(level, boss)
        val session = GameSession(
            sessionId = "progress",
            seed = 7L,
            map = map,
            playerPose = PlayerPose(exitGate.x + 0.5f, exitGate.y + 0.5f, yaw = 0f),
            keyPickups = KeySpawner.spawn(level, seed = 7L).toMutableList(),
            bossRoom = boss,
            exitGate = exitGate,
        )

        LevelProgressSystem.apply(session, InputSyncRequest(interact = true))

        assertFalse(session.levelCompleted)
    }

    @Test
    fun `interact near key picks it up`() {
        val session = GameSession(
            sessionId = "key",
            seed = 1L,
            map = TileMap(5, 5, Array(25) { TileType.FLOOR }),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
            keyPickups = mutableListOf(KeyPickup(id = 0, x = 2.5f, y = 2.5f)),
            exitGate = GridPos(4, 4),
        )

        val events = LevelProgressSystem.apply(session, InputSyncRequest(interact = true))

        assertEquals(1, session.keysCollected)
        assertTrue(events.any { it is GameEvent.KeyCollected })
    }

    @Test
    fun `walking near key without interact does not collect`() {
        val session = GameSession(
            sessionId = "key",
            seed = 1L,
            map = TileMap(5, 5, Array(25) { TileType.FLOOR }),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
            keyPickups = mutableListOf(KeyPickup(id = 0, x = 2.5f, y = 2.5f)),
            exitGate = GridPos(4, 4),
        )

        LevelProgressSystem.apply(session, InputSyncRequest(interact = false))

        assertEquals(0, session.keysCollected)
    }
}
