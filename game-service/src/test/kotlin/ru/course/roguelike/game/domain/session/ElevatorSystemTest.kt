package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class ElevatorSystemTest {
    // 3x3 уровни: пол со лифтом в центре (1,1) на обоих уровнях.
    private fun levelWithCenterElevator(): TileMap {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.ELEVATOR
        return TileMap(3, 3, tiles)
    }

    private val ground = levelWithCenterElevator()
    private val upper = levelWithCenterElevator()

    // Герой стартует в углу (0.5,0.5) — НЕ на лифте.
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

    @Test
    fun `entering an elevator switches to the other level`() {
        val session = twoLevelSession()
        assertNull(ElevatorSystem.apply(session)) // ещё в углу

        session.moveTo(1.5f, 1.5f) // на лифт
        val event = ElevatorSystem.apply(session)

        assertEquals(1, session.currentLevel)
        assertSame(upper, session.activeMap)
        assertTrue(event is GameEvent.LevelChanged && event.level == 1)
    }

    @Test
    fun `standing on an elevator does not repeatedly toggle`() {
        val session = twoLevelSession()
        session.moveTo(1.5f, 1.5f)
        ElevatorSystem.apply(session) // -> level 1

        assertNull(ElevatorSystem.apply(session)) // всё ещё стоим — не дёргаем
        assertEquals(1, session.currentLevel)
    }

    @Test
    fun `stepping off and back onto an elevator switches again`() {
        val session = twoLevelSession()
        session.moveTo(1.5f, 1.5f)
        ElevatorSystem.apply(session) // level 1

        session.moveTo(0.5f, 0.5f) // сходим с лифта на верхнем уровне
        ElevatorSystem.apply(session)
        session.moveTo(1.5f, 1.5f) // снова на лифт
        val event = ElevatorSystem.apply(session)

        assertEquals(0, session.currentLevel)
        assertSame(ground, session.activeMap)
        assertTrue(event is GameEvent.LevelChanged && event.level == 0)
    }

    @Test
    fun `single level session never changes level`() {
        val session = GameSession(
            sessionId = "single",
            seed = 1L,
            map = levelWithCenterElevator(),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f), // прямо на лифте
        )
        assertNull(ElevatorSystem.apply(session))
        assertEquals(0, session.currentLevel)
    }
}
