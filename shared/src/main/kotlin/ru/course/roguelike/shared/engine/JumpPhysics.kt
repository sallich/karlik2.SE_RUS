package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.FpsConstants

/** Вертикальная физика прыжка (общая для сервера и клиента). */
object JumpPhysics {
    data class State(val height: Float, val verticalVelocity: Float)

    fun tick(
        height: Float,
        verticalVelocity: Float,
        jumpRequested: Boolean,
        deltaMs: Int,
        surfaceSupport: Float = 0f,
    ): State {
        val dt = deltaMs.coerceIn(1, 200) / 1000f
        var h = height
        var v = verticalVelocity
        val grounded = h <= surfaceSupport + 0.001f && v <= 0f
        if (jumpRequested && grounded) {
            v = FpsConstants.JUMP_INITIAL_VELOCITY
            h = surfaceSupport
        }
        v -= FpsConstants.GRAVITY * dt
        h += v * dt
        if (h < surfaceSupport) {
            h = surfaceSupport
            if (v < 0f) v = 0f
        } else if (h >= FpsConstants.MAX_JUMP_HEIGHT && v > 0f) {
            h = FpsConstants.MAX_JUMP_HEIGHT
            v = 0f
        }
        return State(h, v)
    }
}
