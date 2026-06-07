package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
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

    private val ground = levelWithCenterElevator()
    private val upper = levelWithCenterElevator()

    private fun twoLevelSession() = GameSession(
        sessionId = "t",
        seed = 1L,
        map = ground,
        playerPose = PlayerPose(0.5f, 0.5f, yaw = 0f),
        secondLevel = upper,
    )

    private fun GameSession.moveTo(x: Float, y: Float) {
        playerPose = PlayerPose(x, y, yaw = 0f)
    }

    private fun GameSession.rideElevatorToLevelSwitch(): GameEvent? {
        moveTo(1.5f, 1.5f)
        var event: GameEvent? = null
        repeat(120) {
            ElevatorSystem.apply(this, deltaMs = 16)?.let { event = it }
            if (event != null) return event
        }
        error("elevator did not switch level")
    }

    @Test
    fun `entering an elevator animates up and switches level at peak`() {
        val session = twoLevelSession()
        assertNull(ElevatorSystem.apply(session, 16))

        val event = session.rideElevatorToLevelSwitch()

        assertEquals(1, session.currentLevel)
        assertSame(upper, session.activeMap)
        assertTrue(event is GameEvent.LevelChanged && event.level == 1)
        assertEquals(ElevatorPhase.DESCENDING, session.elevatorPhase)
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, session.playerPose.height, 0.05f)
    }

    @Test
    fun `after level switch player descends from peak to ground`() {
        val session = twoLevelSession()
        session.rideElevatorToLevelSwitch()
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, session.playerPose.height, 0.05f)
        repeat(120) { ElevatorSystem.apply(session, 16) }
        assertEquals(ElevatorPhase.IDLE, session.elevatorPhase)
        assertEquals(0f, session.playerPose.height, 0.05f)
    }

    @Test
    fun `standing on an elevator does not repeatedly toggle`() {
        val session = twoLevelSession()
        session.rideElevatorToLevelSwitch()
        repeat(30) { ElevatorSystem.apply(session, 16) }

        assertEquals(1, session.currentLevel)
        assertNull(ElevatorSystem.apply(session, 16))
    }

    @Test
    fun `stepping off and back onto an elevator switches again`() {
        val session = twoLevelSession()
        session.rideElevatorToLevelSwitch()
        repeat(80) { ElevatorSystem.apply(session, 16) }

        session.moveTo(0.5f, 0.5f)
        ElevatorSystem.apply(session, 16)
        session.rideElevatorToLevelSwitch()

        assertEquals(0, session.currentLevel)
        assertSame(ground, session.activeMap)
    }

    @Test
    fun `single level session never changes level`() {
        val session = GameSession(
            sessionId = "single",
            seed = 1L,
            map = levelWithCenterElevator(),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
        )
        assertNull(ElevatorSystem.apply(session, 16))
        assertEquals(0, session.currentLevel)
    }

    @Test
    fun `elevator peak matches configured travel height`() {
        var height = 0f
        var phase = ElevatorPhase.ASCENDING
        var peak = 0f
        var switched = false
        repeat(100) {
            if (switched) return@repeat
            val result = ElevatorPhysics.tick(phase, height, 16)
            phase = result.phase
            height = result.height
            peak = maxOf(peak, height)
            if (result.levelSwitch) switched = true
        }
        assertEquals(ElevatorPhysics.PEAK_HEIGHT, peak, 0.02f)
    }
}
