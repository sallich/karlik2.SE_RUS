package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.ElevatorPhysics
import ru.course.roguelike.shared.model.TileType

/**
 * Лифт как длинный прыжок над колоннами на том же ярусе (issue #3).
 *
 * При входе на лифт герой поднимается с анимацией, на пике перелетает препятствия
 * и опускается обратно на тот же лабиринт. Карта не меняется.
 */
object ElevatorSystem {
    fun apply(session: GameSession, deltaMs: Int) {
        val onElevatorNow = session.map
            .getTileAt(session.playerPose.x, session.playerPose.y) == TileType.ELEVATOR

        if (session.elevatorPhase == ElevatorPhase.IDLE) {
            if (onElevatorNow && !session.onElevator) {
                session.elevatorPhase = ElevatorPhase.ASCENDING
            } else {
                session.onElevator = onElevatorNow
                return
            }
        }

        session.onElevator = true
        val result = ElevatorPhysics.tick(session.elevatorPhase, session.playerPose.height, deltaMs)
        session.elevatorPhase = result.phase
        session.playerVerticalVelocity = result.verticalVelocity
        session.playerPose = session.playerPose.copy(height = result.height)

        if (result.phase == ElevatorPhase.IDLE) {
            session.onElevator = onElevatorNow
        }
    }
}
