package ru.course.roguelike.game.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.command.CommandDispatcher
import ru.course.roguelike.game.domain.command.SyncInputCommand
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

class LavaDamageDispatchTest {
    private val bus = GameEventBus()
    private val dispatcher = CommandDispatcher(eventBus = bus)

    // Игрок стоит на лаве в центре 3x3 карты; ввод без движения, чтобы остаться на ней.
    private fun sessionOnLava(hp: Int): GameSession {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.LAVA
        return GameSession(
            sessionId = "lava",
            seed = 1L,
            map = TileMap(3, 3, tiles),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
            playerHp = hp,
            locationCompletionAwarded = true,
        )
    }

    @Test
    fun `sync over lava damages player but stays in exploration while alive`() {
        val session = sessionOnLava(hp = 100)
        val events = mutableListOf<GameEvent>()
        bus.subscribe { events += it }

        dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(deltaMs = 1000)))

        assertEquals(80, session.playerHp)
        assertEquals(SessionPhase.EXPLORATION, session.phase)
        assertTrue(events.any { it is GameEvent.PlayerDamaged })
    }

    @Test
    fun `lethal lava damage transitions to game over`() {
        val session = sessionOnLava(hp = 10)
        val events = mutableListOf<GameEvent>()
        bus.subscribe { events += it }

        dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(deltaMs = 1000)))

        assertEquals(0, session.playerHp)
        assertEquals(SessionPhase.GAME_OVER, session.phase)
        assertTrue(events.any { it is GameEvent.PlayerDamaged })
        assertTrue(
            events.any { it is GameEvent.PhaseChanged && it.to == SessionPhase.GAME_OVER },
            "expected a PhaseChanged to GAME_OVER",
        )
    }

    @Test
    fun `dead player cannot sync again`() {
        val session = sessionOnLava(hp = 10)
        dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(deltaMs = 1000)))
        assertEquals(SessionPhase.GAME_OVER, session.phase)

        val result = dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(deltaMs = 1000)))
        assertTrue(!result.accepted)
    }
}
