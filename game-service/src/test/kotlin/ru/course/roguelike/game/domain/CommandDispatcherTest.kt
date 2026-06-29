package ru.course.roguelike.game.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.command.CommandDispatcher
import ru.course.roguelike.game.domain.command.LegacyMovementCommand
import ru.course.roguelike.game.domain.command.SyncInputCommand
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.infrastructure.level.TestLevelGenerator
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.protocol.GameActions

class CommandDispatcherTest {
    private val bus = GameEventBus()
    private val dispatcher = CommandDispatcher(eventBus = bus)

    @Test
    fun `sync moves player in exploration`() {
        val level = TestLevelGenerator.generate(1L)
        val session = GameSession(
            sessionId = "s1",
            seed = 1L,
            map = level.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
        )
        val before = session.playerPose
        val result = dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(forward = true, deltaMs = 200)))
        assertTrue(result.accepted)
        assertTrue(session.playerPose != before)
    }

    @Test
    fun `choice phase rejects movement`() {
        val level = TestLevelGenerator.generate(2L)
        val session = GameSession(
            sessionId = "s2",
            seed = 2L,
            phase = SessionPhase.CHOICE,
            map = level.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
        )
        val result = dispatcher.dispatch(
            session,
            LegacyMovementCommand(GameActions.MOVE_NORTH),
        )
        assertFalse(result.accepted)
        assertTrue(result.message.contains("choice", ignoreCase = true))
    }

    @Test
    fun `events published to bus`() {
        val events = mutableListOf<GameEvent>()
        bus.subscribe { events += it }
        val level = TestLevelGenerator.generate(3L)
        val session = GameSession(
            sessionId = "s3",
            seed = 3L,
            map = level.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
        )
        dispatcher.dispatch(session, SyncInputCommand(InputSyncRequest(forward = true, deltaMs = 100)))
        assertTrue(events.any { it is GameEvent.PlayerMoved })
    }

    @Test
    fun `same seed produces same map`() {
        val a = TestLevelGenerator.generate(42L)
        val b = TestLevelGenerator.generate(42L)
        assertEquals(a.map.toFlatList(), b.map.toFlatList())
        assertEquals(a.playerSpawn, b.playerSpawn)
    }
}
