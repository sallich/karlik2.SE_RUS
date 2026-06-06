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
import ru.course.roguelike.shared.model.TileType

class ElevatorDispatchTest {
    private val bus = GameEventBus()
    private val dispatcher = CommandDispatcher(eventBus = bus)

    private fun centerElevatorLevel(): TileMap {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.ELEVATOR
        return TileMap(3, 3, tiles)
    }

    @Test
    fun `sync onto an elevator switches level and is reflected in the snapshot`() {
        val upper = centerElevatorLevel()
        val session = GameSession(
            sessionId = "lift",
            seed = 1L,
            map = centerElevatorLevel(),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f), // на лифте, onElevator=false
            secondLevel = upper,
        )
        val events = mutableListOf<GameEvent>()
        bus.subscribe { events += it }

        dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(deltaMs = 16)))

        assertEquals(1, session.currentLevel)
        assertEquals(1, session.toSnapshot().currentLevel)
        assertTrue(events.any { it is GameEvent.LevelChanged && it.level == 1 })
    }
}
