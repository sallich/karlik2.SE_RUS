package ru.course.roguelike.game.domain.event

import ru.course.roguelike.shared.model.SessionPhase

sealed interface GameEvent {
    data class SessionCreated(val sessionId: String, val seed: Long) : GameEvent

    data class CommandExecuted(val commandName: String, val accepted: Boolean) : GameEvent

    data class PhaseChanged(val from: SessionPhase, val to: SessionPhase) : GameEvent

    data class PlayerMoved(val tick: Long) : GameEvent

    data class PlayerDamaged(val amount: Int, val remainingHp: Int) : GameEvent

    data class LevelChanged(val level: Int) : GameEvent
}
