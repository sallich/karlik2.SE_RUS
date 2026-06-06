package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.ElevatorPhysics
import ru.course.roguelike.shared.model.TileType

/**
 * Переход между ярусами двухуровневой локации (issue #3, лифты).
 *
 * Лифты расположены в одинаковых координатах на обоих уровнях («стопкой»).
 * При входе на лифт герой поднимается с анимацией, на пике меняется активный
 * ярус (та же локация, другой этаж), затем опускается на новый пол.
 */
object ElevatorSystem {
    fun apply(session: GameSession, deltaMs: Int): GameEvent? {
        if (session.secondLevel == null) {
            session.onElevator = false
            return null
        }

        val onElevatorNow = session.activeMap
            .getTileAt(session.playerPose.x, session.playerPose.y) == TileType.ELEVATOR

        if (session.elevatorPhase == ElevatorPhase.IDLE) {
            if (onElevatorNow && !session.onElevator) {
                session.elevatorPhase = ElevatorPhase.ASCENDING
            } else {
                session.onElevator = onElevatorNow
                return null
            }
        }

        session.onElevator = true
        val result = ElevatorPhysics.tick(session.elevatorPhase, session.playerPose.height, deltaMs)
        session.elevatorPhase = result.phase
        session.playerVerticalVelocity = result.verticalVelocity
        session.playerPose = session.playerPose.copy(height = result.height)

        if (result.levelSwitch) {
            session.currentLevel = 1 - session.currentLevel
            session.playerPose = session.playerPose.copy(height = ElevatorPhysics.PEAK_HEIGHT)
            session.playerVerticalVelocity = result.verticalVelocity
            return GameEvent.LevelChanged(session.currentLevel)
        }

        if (result.phase == ElevatorPhase.IDLE) {
            session.onElevator = onElevatorNow
        }
        return null
    }
}
