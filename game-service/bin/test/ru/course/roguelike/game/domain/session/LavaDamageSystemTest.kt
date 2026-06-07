package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class LavaDamageSystemTest {
    // Карта 3x3, центральный тайл (1,1) — заданного типа, игрок стоит в (1.5,1.5).
    private fun sessionOnCenterTile(tile: TileType, hp: Int = 100): GameSession {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = tile
        return GameSession(
            sessionId = "t",
            seed = 1L,
            map = TileMap(3, 3, tiles),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
            playerHp = hp,
        )
    }

    @Test
    fun `standing on lava reduces hp over time`() {
        val session = sessionOnCenterTile(TileType.LAVA)
        val event = LavaDamageSystem.apply(session, deltaMs = 1000)

        assertEquals(80, session.playerHp) // 20 HP/sec * 1s
        assertTrue(event is GameEvent.PlayerDamaged)
        event as GameEvent.PlayerDamaged
        assertEquals(20, event.amount)
        assertEquals(80, event.remainingHp)
    }

    @Test
    fun `standing on floor deals no damage and resets the buffer`() {
        val session = sessionOnCenterTile(TileType.FLOOR)
        session.lavaDamageBuffer = 0.9f

        val event = LavaDamageSystem.apply(session, deltaMs = 1000)

        assertNull(event)
        assertEquals(100, session.playerHp)
        assertEquals(0f, session.lavaDamageBuffer)
    }

    @Test
    fun `sub-integer damage accumulates across short ticks`() {
        val session = sessionOnCenterTile(TileType.LAVA)

        // 20 HP/sec * 40ms = 0.8 HP -> no whole point lost yet.
        assertNull(LavaDamageSystem.apply(session, deltaMs = 40))
        assertEquals(100, session.playerHp)

        // +0.8 = 1.6 accumulated -> exactly 1 HP lost, 0.6 carried over.
        val event = LavaDamageSystem.apply(session, deltaMs = 40)
        assertEquals(99, session.playerHp)
        assertTrue(event is GameEvent.PlayerDamaged)
    }

    @Test
    fun `hp clamps at zero and reports actual loss`() {
        val session = sessionOnCenterTile(TileType.LAVA, hp = 5)
        val event = LavaDamageSystem.apply(session, deltaMs = 1000)

        assertEquals(0, session.playerHp)
        event as GameEvent.PlayerDamaged
        assertEquals(5, event.amount)
        assertEquals(0, event.remainingHp)
    }

    @Test
    fun `leaving lava stops the damage`() {
        val session = sessionOnCenterTile(TileType.LAVA)
        LavaDamageSystem.apply(session, deltaMs = 1000)
        assertEquals(80, session.playerHp)

        // Step off the lava tile onto floor.
        session.playerPose = PlayerPose(0.5f, 0.5f, yaw = 0f)
        val event = LavaDamageSystem.apply(session, deltaMs = 1000)

        assertNull(event)
        assertEquals(80, session.playerHp)
    }
}
