package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.VerticalMotion
import ru.course.roguelike.shared.model.FpsConstants
import kotlin.math.ceil

object PlayerVerticalMotion {
    fun applyJump(session: GameSession, input: InputSyncRequest) {
        if (session.elevatorPhase != ElevatorPhase.IDLE) return

        val totalMs = input.deltaMs.coerceIn(1, 250)
        val steps = ceil(totalMs.toFloat() / FpsConstants.MOVEMENT_SUBSTEP_MS).toInt()
            .coerceIn(1, 12)
        val msPerStep = totalMs / steps
        var h = session.playerPose.height
        var v = session.playerVerticalVelocity
        var jumpPending = input.jump
        val map = session.activeMap
        val x = session.playerPose.x
        val y = session.playerPose.y
        repeat(steps) {
            val state = VerticalMotion.tick(
                map = map,
                x = x,
                y = y,
                height = h,
                verticalVelocity = v,
                jumpRequested = jumpPending,
                deltaMs = msPerStep,
            )
            h = state.height
            v = state.verticalVelocity
            jumpPending = false
        }
        session.playerVerticalVelocity = v
        session.playerPose = session.playerPose.copy(height = h)
    }
}
