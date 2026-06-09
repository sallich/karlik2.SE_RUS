package ru.course.roguelike.game.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.command.SyncInputPipeline
import ru.course.roguelike.game.domain.session.ElevatorSystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.PlayerVerticalMotion
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.ElevatorPhysics
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class ElevatorDispatchTest {
    private fun centerElevatorLevel(): TileMap {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.ELEVATOR
        return TileMap(3, 3, tiles)
    }

    private fun rideToPeak(session: GameSession, tick: () -> Unit) {
        repeat(120) {
            tick()
            if (session.playerPose.height >= ElevatorPhysics.PEAK_HEIGHT - 0.05f) return
        }
        error("elevator did not reach peak, height=${session.playerPose.height}, phase=${session.elevatorPhase}")
    }

    @Test
    fun `elevator height survives movement and jump in sync pipeline`() {
        val session = GameSession(
            sessionId = "lift-parts",
            seed = 1L,
            map = centerElevatorLevel(),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
        )
        val input = InputSyncRequest(deltaMs = 16)

        rideToPeak(session) {
            ElevatorSystem.apply(session, input.deltaMs)
            PlayerVerticalMotion.applyJump(session, input)
            session.playerPose = FpsMovementSystem.applyInput(session.activeMap, session.playerPose, input)
        }

        assertEquals(ElevatorPhysics.PEAK_HEIGHT, session.playerPose.height, 0.05f)
        assertTrue(session.elevatorPhase != ElevatorPhase.IDLE)
    }

    @Test
    fun `sync onto an elevator animates without switching level`() {
        val session = GameSession(
            sessionId = "lift",
            seed = 1L,
            map = centerElevatorLevel(),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
        )
        val input = InputSyncRequest(deltaMs = 16)

        rideToPeak(session) {
            SyncInputPipeline.runPlayer(session, input)
        }

        assertEquals(0, session.currentLevel)
        assertEquals(0, session.toSnapshot().currentLevel)
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, session.playerPose.height, 0.05f)
    }
}
