package ru.course.roguelike.game.domain.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.StarterLoadout
import ru.course.roguelike.game.domain.session.ExitGatePlacer
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.ItemPickup
import ru.course.roguelike.game.domain.session.KeyPickup
import ru.course.roguelike.game.domain.session.KeySpawner
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class SyncInputInteractTest {
    @Test
    fun `interact collects key at pre-move pose while strafing away`() {
        val session = GameSession(
            sessionId = "move-key",
            seed = 1L,
            map = TileMap(5, 5, Array(25) { TileType.FLOOR }),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
            keyPickups = mutableListOf(KeyPickup(id = 0, x = 2.5f, y = 2.6f)),
            exitGate = GridPos(4, 4),
        )

        val result = SyncInputCommand(
            InputSyncRequest(strafeRight = true, interact = true, deltaMs = 250),
        ).execute(session)

        assertTrue(result.accepted)
        assertEquals(1, session.keysCollected)
        assertTrue(result.events.any { it is GameEvent.KeyCollected })
    }

    @Test
    fun `interact picks up weapon at pre-move pose while moving forward`() {
        val session = GameSession(
            sessionId = "move-weapon",
            seed = 1L,
            map = TileMap(5, 5, Array(25) { TileType.FLOOR }),
            playerPose = PlayerPose(2.4f, 2.5f, yaw = 0f),
        ).also { StarterLoadout.apply(it) }
        session.itemPickups.add(
            ItemPickup(id = 0, kind = ItemKind.WEAPON_PISTOL, x = 2.5f, y = 2.5f),
        )

        val result = SyncInputCommand(
            InputSyncRequest(forward = true, interact = true, deltaMs = 200),
        ).execute(session)

        assertTrue(result.accepted)
        assertTrue(session.itemPickups.single().collected)
        assertTrue(result.events.any { it is GameEvent.ItemCollected })
    }

    @Test
    fun `interact opens exit at pre-move pose while leaving gate tile`() {
        val level = LabyrinthLevelGenerator.generate(seed = 99L)
        val boss = level.rooms.single { it.isBoss }
        val (map, exitGate) = ExitGatePlacer.place(level, boss)
        val keys = KeySpawner.spawn(level, seed = 99L).onEach { it.collected = true }
        val session = GameSession(
            sessionId = "move-exit",
            seed = 99L,
            map = map,
            playerPose = PlayerPose(exitGate.x + 0.5f, exitGate.y + 0.5f, yaw = 0f),
            keyPickups = keys.toMutableList(),
            bossRoom = boss,
            exitGate = exitGate,
        )

        val result = SyncInputCommand(
            InputSyncRequest(forward = true, interact = true, deltaMs = 250),
        ).execute(session)

        assertTrue(result.accepted)
        assertTrue(session.levelCompleted)
        assertTrue(result.events.any { it is GameEvent.LevelCompleted })
    }
}
