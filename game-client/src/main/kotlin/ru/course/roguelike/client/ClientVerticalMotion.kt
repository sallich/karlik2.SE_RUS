package ru.course.roguelike.client

import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.ElevatorPhysics
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.engine.VerticalMotion
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

/** Локальная вертикальная физика (прыжок и лифт) на клиенте. */
object ClientVerticalMotion {
    data class Result(
        val pose: PlayerPose,
        val verticalVelocity: Float,
        val elevatorPhase: ElevatorPhase,
        val levelSwitched: Boolean,
    )

    fun tick(
        map: TileMap,
        pose: PlayerPose,
        verticalVelocity: Float,
        elevatorPhase: ElevatorPhase,
        wasOnElevator: Boolean,
        twoLevel: Boolean,
        jumpRequested: Boolean,
        deltaMs: Int,
    ): Result {
        if (twoLevel) {
            val onElevator = map.getTileAt(pose.x, pose.y) == TileType.ELEVATOR
            var phase = elevatorPhase
            if (phase == ElevatorPhase.IDLE && onElevator && !wasOnElevator) {
                phase = ElevatorPhase.ASCENDING
            }
            if (phase != ElevatorPhase.IDLE) {
                val lift = ElevatorPhysics.tick(phase, pose.height, deltaMs)
                return Result(
                    pose = pose.copy(height = lift.height),
                    verticalVelocity = lift.verticalVelocity,
                    elevatorPhase = lift.phase,
                    levelSwitched = lift.levelSwitch,
                )
            }
        }

        val jump = VerticalMotion.tick(
            map = map,
            x = pose.x,
            y = pose.y,
            height = pose.height,
            verticalVelocity = verticalVelocity,
            jumpRequested = jumpRequested,
            deltaMs = deltaMs,
        )
        return Result(
            pose = pose.copy(height = jump.height),
            verticalVelocity = jump.verticalVelocity,
            elevatorPhase = ElevatorPhase.IDLE,
            levelSwitched = false,
        )
    }
}
