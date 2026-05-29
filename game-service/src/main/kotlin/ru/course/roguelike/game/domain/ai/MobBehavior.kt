package ru.course.roguelike.game.domain.ai

/**
 * Strategy: поведение моба подменяется без изменения CombatSystem.
 * Реализации появятся с encounter-слоем.
 */
interface MobBehavior {
    fun decide(context: MobDecisionContext): MobIntent
}

data class MobDecisionContext(
    val mobId: Long,
    val archetype: MobArchetype,
)

enum class MobArchetype {
    RUSHER,
    SHOOTER,
    ELITE,
}

sealed interface MobIntent {
    data object Idle : MobIntent
    data class MoveTo(val x: Int, val y: Int) : MobIntent
    data class Attack(val targetId: Long) : MobIntent
}
