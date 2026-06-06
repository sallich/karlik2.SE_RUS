package ru.course.roguelike.game.domain.event

import ru.course.roguelike.shared.model.ItemKind
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

    data class ExperienceGained(val amount: Int, val source: String, val totalXp: Int) : GameEvent

    data class PlayerLevelUp(val newLevel: Int, val maxHp: Int, val attackDamage: Int) : GameEvent

    data class LocationCompleted(val bonusXp: Int) : GameEvent

    data class KeyCollected(val keyId: Int, val totalCollected: Int) : GameEvent

    data class LevelCompleted(val keysCollected: Int, val keysRequired: Int) : GameEvent

    data class ItemCollected(val itemId: Int, val kind: ItemKind) : GameEvent

    data class ItemDropped(val itemId: Int, val kind: ItemKind, val x: Float, val y: Float) : GameEvent

    data class PlayerHealed(val amount: Int, val hp: Int) : GameEvent

    data class WeaponUpgraded(val bonus: Int, val attackDamage: Int) : GameEvent

    data class AmmoChanged(val delta: Int, val ammo: Int) : GameEvent
}
