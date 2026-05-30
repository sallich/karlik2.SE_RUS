package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.model.TileType

/**
 * Переход между уровнями двухуровневой локации (issue #3, лифты).
 *
 * Лифты расположены в одинаковых координатах на обоих уровнях («стопкой»),
 * поэтому вход на тайл ELEVATOR переключает активный уровень, сохраняя позицию
 * героя по X/Y. Переход срабатывает только в момент входа на лифт
 * ([GameSession.onElevator] не даёт зациклиться, пока герой стоит на нём).
 */
object ElevatorSystem {
    /**
     * Проверяет тайл под героем и при входе на лифт переключает уровень.
     * Возвращает [GameEvent.LevelChanged], если уровень сменился, иначе null.
     */
    fun apply(session: GameSession): GameEvent? {
        if (session.secondLevel == null) return null

        val onElevatorNow = session.activeMap
            .getTileAt(session.playerPose.x, session.playerPose.y) == TileType.ELEVATOR

        if (onElevatorNow && !session.onElevator) {
            session.currentLevel = 1 - session.currentLevel
            // Герой оказывается на парном лифте другого уровня — помечаем, чтобы
            // не переключиться снова на следующем тике.
            session.onElevator = true
            return GameEvent.LevelChanged(session.currentLevel)
        }

        session.onElevator = onElevatorNow
        return null
    }
}
