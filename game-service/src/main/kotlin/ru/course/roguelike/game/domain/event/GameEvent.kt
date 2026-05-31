package ru.course.roguelike.game.domain.event

import ru.course.roguelike.shared.model.SessionPhase

sealed interface GameEvent {
    data class SessionCreated(val sessionId: String, val seed: Long) : GameEvent

    data class CommandExecuted(val commandName: String, val accepted: Boolean) : GameEvent

    data class PhaseChanged(val from: SessionPhase, val to: SessionPhase) : GameEvent

    data class PlayerMoved(val tick: Long) : GameEvent

    data class PlayerDamaged(val amount: Int, val remainingHp: Int) : GameEvent

    data class MobDamaged(val mobId: Long, val amount: Int, val remainingHp: Int) : GameEvent

    data class MobKilled(val mobId: Long) : GameEvent

    data class LevelChanged(val level: Int) : GameEvent

    data class KeyCollected(val keyId: Int, val totalCollected: Int) : GameEvent

    data class LevelCompleted(val keysCollected: Int, val keysRequired: Int) : GameEvent
}
