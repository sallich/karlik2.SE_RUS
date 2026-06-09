package ru.course.roguelike.client

import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.ElevatorPhysics
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.engine.VerticalMotion
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

/** Локальная вертикальная физика (прыжок и лифт) на клиенте. */
object ClientVerticalMotion {
    data class TickInput(
        val map: TileMap,
        val pose: PlayerPose,
        val verticalVelocity: Float,
        val elevatorPhase: ElevatorPhase,
        val wasOnElevator: Boolean,
        val twoLevel: Boolean,
        val jumpRequested: Boolean,
        val deltaMs: Int,
    )

    data class Result(
        val pose: PlayerPose,
        val verticalVelocity: Float,
        val elevatorPhase: ElevatorPhase,
    )

    fun tick(input: TickInput): Result {
        if (input.twoLevel) {
            val onElevator = input.map.getTileAt(input.pose.x, input.pose.y) == TileType.ELEVATOR
            var phase = input.elevatorPhase
            if (phase == ElevatorPhase.IDLE && onElevator && !input.wasOnElevator) {
                phase = ElevatorPhase.ASCENDING
            }
            if (phase != ElevatorPhase.IDLE) {
                val lift = ElevatorPhysics.tick(phase, input.pose.height, input.deltaMs)
                return Result(
                    pose = input.pose.copy(height = lift.height),
                    verticalVelocity = lift.verticalVelocity,
                    elevatorPhase = lift.phase,
                )
            }
        }

        val jump = VerticalMotion.tick(
            map = input.map,
            x = input.pose.x,
            y = input.pose.y,
            height = input.pose.height,
            verticalVelocity = input.verticalVelocity,
            jumpRequested = input.jumpRequested,
            deltaMs = input.deltaMs,
        )
        return Result(
            pose = input.pose.copy(height = jump.height),
            verticalVelocity = jump.verticalVelocity,
            elevatorPhase = ElevatorPhase.IDLE,
        )
    }
}
