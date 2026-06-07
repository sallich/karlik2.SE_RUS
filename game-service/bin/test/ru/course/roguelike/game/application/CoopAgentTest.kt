package ru.course.roguelike.game.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.InputSyncRequest

class CoopAgentTest {
    @Test
    fun `coop session spawns agent beside player`() {
        val engine = GameEngine()
        val snap = engine.createSession(seed = 42L, twoLevel = false, coopAgent = true)
        assertNotNull(snap.agent)
        assertEquals(snap.sessionId, snap.sessionId)
    }

    @Test
    fun `agent sync moves agent pose independently`() {
        val engine = GameEngine()
        val snap = engine.createSession(seed = 42L, coopAgent = true)
        val startPlayer = snap.player.pose
        val startAgent = snap.agent!!.pose
        val moved = engine.syncInput(
            snap.sessionId,
            InputSyncRequest(
                actor = "agent",
                clientYaw = 0f,
                clientPitch = 0f,
                forward = true,
                deltaMs = 120,
            ),
        )!!
        assertNotNull(moved.snapshot.agent)
        assertEquals(startPlayer.x, moved.snapshot.player.pose.x, 0.01f)
        assert(moved.snapshot.agent!!.pose.x > startAgent.x)
    }
}
