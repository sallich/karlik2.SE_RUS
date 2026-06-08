package ru.course.roguelike.game.domain.session

/** Состояние таймера зачистки одной комнаты (параллельно [GameSession.rooms]). */
data class RoomEngagementState(
    val roomIndex: Int,
    var timerStartedAtMs: Long? = null,
    var cleared: Boolean = false,
    var reinforcementsTriggered: Boolean = false,
)
