package ru.course.roguelike.game.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.game.domain.combat.MobSpawner
import ru.course.roguelike.game.domain.command.CommandDispatcher
import ru.course.roguelike.game.domain.command.SyncInputCommand
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

class CombatDispatchTest {
    private val bus = GameEventBus()
    private val dispatcher = CommandDispatcher(eventBus = bus)

    @Test
    fun `new session spawns melee and ranged mobs`() {
        val snap = GameEngine().createSession(seed = 42L)
        assertTrue(snap.mobs.size >= 2)
        assertTrue(snap.mobs.any { it.kind == MobKind.MELEE })
        assertTrue(snap.mobs.any { it.kind == MobKind.RANGED })
    }

    @Test
    fun `new session includes keys and boss room metadata`() {
        val snap = GameEngine().createSession(seed = 42L)
        assertEquals(3, snap.keysRequired)
        assertEquals(0, snap.keysCollected)
        // Ключи — призы комнат (issue #24): они спрятаны, пока комната не зачищена,
        // поэтому видимых ключей в свежей сессии нет, хотя требуется собрать все 3.
        assertEquals(0, snap.keyPickups.size)
        assertTrue(snap.bossRoom != null)
        assertTrue(snap.exitGate != null)
    }

    @Test
    fun `hp reaching zero during sync transitions to game over`() {
        val session = arenaSession()
        session.playerHp = 1
        val mob = session.mobs.first()
        mob.x = session.playerPose.x + 0.6f
        mob.y = session.playerPose.y
        mob.attackCooldownMs = 0

        dispatcher.dispatch(
            session,
            SyncInputCommand(
                InputSyncRequest(deltaMs = 50, clientYaw = 0f, clientPitch = 0f),
            ),
        )
        mob.attackCooldownMs = 0
        dispatcher.dispatch(
            session,
            SyncInputCommand(
                InputSyncRequest(deltaMs = 50, clientYaw = 0f, clientPitch = 0f),
            ),
        )

        assertEquals(0, session.playerHp)
        assertEquals(SessionPhase.GAME_OVER, session.phase)
    }

    @Test
    fun `sync attack spawns player projectile through command pipeline`() {
        val session = arenaSession()
        dispatcher.dispatch(
            session,
            SyncInputCommand(
                InputSyncRequest(
                    attack = true,
                    deltaMs = 50,
                    clientYaw = 0f,
                    clientPitch = 0f,
                ),
            ),
        )

        assertEquals(1, session.projectiles.size)
        assertTrue(session.projectiles.first().fromPlayer)
    }

    private fun arenaSession(): GameSession {
        val tiles = Array(25) { TileType.FLOOR }
        for (x in 0 until 5) {
            tiles[x] = TileType.WALL
            tiles[4 * 5 + x] = TileType.WALL
            tiles[x * 5] = TileType.WALL
            tiles[x * 5 + 4] = TileType.WALL
        }
        return GameSession(
            sessionId = "arena",
            seed = 1L,
            map = TileMap(5, 5, tiles),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
        ).also { session ->
            val room = ru.course.roguelike.game.domain.level.Room(1, 1, 3, 3)
            session.mobs.add(MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, room))
        }
    }
}
