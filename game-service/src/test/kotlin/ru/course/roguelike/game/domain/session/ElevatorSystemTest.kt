package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.ElevatorPhysics
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class ElevatorSystemTest {
    private fun levelWithCenterElevator(): TileMap {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.ELEVATOR
        return TileMap(3, 3, tiles)
    }

    private fun elevatorSession() = GameSession(
        sessionId = "t",
        seed = 1L,
        map = levelWithCenterElevator(),
        playerPose = PlayerPose(0.5f, 0.5f, yaw = 0f),
    )

    private fun GameSession.moveTo(x: Float, y: Float) {
        playerPose = PlayerPose(x, y, yaw = 0f)
    }

    private fun GameSession.rideElevatorToPeak() {
        moveTo(1.5f, 1.5f)
        repeat(120) {
            ElevatorSystem.apply(this, deltaMs = 16)
            if (elevatorPhase == ElevatorPhase.DESCENDING) return
        }
        error("elevator did not reach peak")
    }

    @Test
    fun `entering an elevator animates up without changing level`() {
        val session = elevatorSession()
        ElevatorSystem.apply(session, 16)
        assertEquals(ElevatorPhase.IDLE, session.elevatorPhase)

        session.rideElevatorToPeak()

        assertEquals(0, session.currentLevel)
        assertEquals(ElevatorPhase.DESCENDING, session.elevatorPhase)
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, session.playerPose.height, 0.05f)
    }

    @Test
    fun `after peak player descends back to ground on the same map`() {
        val session = elevatorSession()
        session.rideElevatorToPeak()
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, session.playerPose.height, 0.05f)
        repeat(120) { ElevatorSystem.apply(session, 16) }
        assertEquals(ElevatorPhase.IDLE, session.elevatorPhase)
        assertEquals(0f, session.playerPose.height, 0.05f)
        assertEquals(0, session.currentLevel)
    }

    @Test
    fun `standing on an elevator does not repeatedly trigger`() {
        val session = elevatorSession()
        session.rideElevatorToPeak()
        repeat(80) { ElevatorSystem.apply(session, 16) }

        assertEquals(0, session.currentLevel)
        assertEquals(ElevatorPhase.IDLE, session.elevatorPhase)
    }

    @Test
    fun `stepping off and back onto an elevator triggers another ride`() {
        val session = elevatorSession()
        session.rideElevatorToPeak()
        repeat(80) { ElevatorSystem.apply(session, 16) }

        session.moveTo(0.5f, 0.5f)
        ElevatorSystem.apply(session, 16)
        session.rideElevatorToPeak()

        assertEquals(0, session.currentLevel)
        assertEquals(ElevatorPhase.DESCENDING, session.elevatorPhase)
    }

    @Test
    fun `map without elevator does nothing`() {
        val session = GameSession(
            sessionId = "single",
            seed = 1L,
            map = TileMap(3, 3, Array(9) { TileType.FLOOR }),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
        )
        ElevatorSystem.apply(session, 16)
        assertEquals(0, session.currentLevel)
        assertEquals(ElevatorPhase.IDLE, session.elevatorPhase)
    }

    @Test
    fun `elevator peak matches configured travel height`() {
        var height = 0f
        var phase = ElevatorPhase.ASCENDING
        var peak = 0f
        repeat(100) {
            val result = ElevatorPhysics.tick(phase, height, 16)
            phase = result.phase
            height = result.height
            peak = maxOf(peak, height)
            if (phase == ElevatorPhase.DESCENDING && height <= ElevatorPhysics.PEAK_HEIGHT) return@repeat
        }
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, peak, 0.02f)
    }
}
