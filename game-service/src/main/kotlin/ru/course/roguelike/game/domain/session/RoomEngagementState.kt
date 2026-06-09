package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.model.GridPos

/** Состояние таймера зачистки одной комнаты (параллельно [GameSession.rooms]). */
data class RoomEngagementState(
    val roomIndex: Int,
    var timerStartedAtMs: Long? = null,
    var cleared: Boolean = false,
    var reinforcementsTriggered: Boolean = false,
    /** Ячейки дверных проёмов комнаты (issue #24), запираются до зачистки. */
    val doorways: List<GridPos> = emptyList(),
    /** Заперты ли сейчас двери комнаты (герой внутри, комната ещё не зачищена). */
    var doorsLocked: Boolean = false,
    /** Герой вошёл в комнату через дверь (E). */
    var entered: Boolean = false,
)
