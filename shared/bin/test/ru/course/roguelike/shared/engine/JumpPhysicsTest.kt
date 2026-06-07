package ru.course.roguelike.shared.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.FpsConstants

class JumpPhysicsTest {
    @Test
    fun `jump accumulates height across frames and returns to ground`() {
        var h = 0f
        var v = 0f
        var peak = 0f
        repeat(40) {
            val state = JumpPhysics.tick(h, v, jumpRequested = it == 0, deltaMs = 16)
            h = state.height
            v = state.verticalVelocity
            peak = maxOf(peak, h)
        }
        assertTrue(peak >= FpsConstants.MAX_JUMP_HEIGHT * 0.85f, "peak was $peak")
        assertEquals(0f, h, 0.001f)
        assertEquals(0f, v, 0.001f)
    }

    @Test
    fun `second jump ignored while airborne`() {
        var h = 0f
        var v = 0f
        repeat(5) {
            val state = JumpPhysics.tick(h, v, jumpRequested = true, deltaMs = 16)
            h = state.height
            v = state.verticalVelocity
        }
        assertTrue(h > 0.1f)
    }
}
