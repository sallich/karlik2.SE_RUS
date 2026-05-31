package ru.course.roguelike.game.domain.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.KeyPickup
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.protocol.GameActions

class AgentActionCommandTest {
    @Test
    fun `interact action collects nearby key`() {
        val session = GameSession(
            sessionId = "agent",
            seed = 1L,
            map = TileMap(5, 5, Array(25) { TileType.FLOOR }),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
            keyPickups = mutableListOf(KeyPickup(id = 0, x = 2.5f, y = 2.5f)),
            exitGate = GridPos(4, 4),
        )
        val dispatcher = CommandDispatcher(eventBus = GameEventBus())

        val result = dispatcher.dispatch(session, LegacyMovementCommand(GameActions.INTERACT))

        assertTrue(result.accepted)
        assertEquals(1, session.keysCollected)
        assertTrue(result.events.any { it is GameEvent.KeyCollected })
    }

    @Test
    fun `move_forward produces sync input`() {
        val input = LegacyMovementCommand.inputFor(GameActions.MOVE_FORWARD)
        assertTrue(input.forward)
        assertEquals(GameActions.AGENT_FORWARD_MS, input.deltaMs)
    }
}
