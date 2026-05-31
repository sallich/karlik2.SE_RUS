package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.MobBehavior
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.MobKind

/**
 * Базовая сущность моба. Конкретные типы задают скорость, дистанцию и урон.
 */
sealed class MobEntity(
    val id: Long,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    val kind: MobKind,
    val behavior: MobBehavior,
    var attackCooldownMs: Int = 0,
) {
    abstract val moveSpeed: Float
    abstract val attackRange: Float
    abstract val attackDamage: Int
    abstract val attackCooldownTotalMs: Int

    val alive: Boolean get() = hp > 0

    fun toSnapshot() = ru.course.roguelike.shared.dto.MobSnapshot(
        id = id,
        kind = kind,
        x = x,
        y = y,
        hp = hp,
        maxHp = maxHp,
    )

    class MeleeMob(
        id: Long,
        x: Float,
        y: Float,
        behavior: MobBehavior,
    ) : MobEntity(
        id = id,
        x = x,
        y = y,
        hp = CombatConstants.MELEE_MOB_HP,
        maxHp = CombatConstants.MELEE_MOB_HP,
        kind = MobKind.MELEE,
        behavior = behavior,
    ) {
        override val moveSpeed: Float = CombatConstants.MELEE_MOVE_SPEED
        override val attackRange: Float = CombatConstants.MELEE_ATTACK_RANGE
        override val attackDamage: Int = CombatConstants.MELEE_MOB_DAMAGE
        override val attackCooldownTotalMs: Int = CombatConstants.MELEE_ATTACK_COOLDOWN_MS
    }

    class RangedMob(
        id: Long,
        x: Float,
        y: Float,
        behavior: MobBehavior,
    ) : MobEntity(
        id = id,
        x = x,
        y = y,
        hp = CombatConstants.RANGED_MOB_HP,
        maxHp = CombatConstants.RANGED_MOB_HP,
        kind = MobKind.RANGED,
        behavior = behavior,
    ) {
        override val moveSpeed: Float = CombatConstants.RANGED_MOVE_SPEED
        override val attackRange: Float = CombatConstants.RANGED_ATTACK_RANGE
        override val attackDamage: Int = CombatConstants.RANGED_MOB_DAMAGE
        override val attackCooldownTotalMs: Int = CombatConstants.RANGED_ATTACK_COOLDOWN_MS
    }

    class LlmGuardMob(
        id: Long,
        x: Float,
        y: Float,
        behavior: MobBehavior,
    ) : MobEntity(
        id = id,
        x = x,
        y = y,
        hp = CombatConstants.RANGED_MOB_HP,
        maxHp = CombatConstants.RANGED_MOB_HP,
        kind = MobKind.LLM_GUARD,
        behavior = behavior,
    ) {
        override val moveSpeed: Float = CombatConstants.RANGED_MOVE_SPEED
        override val attackRange: Float = CombatConstants.RANGED_ATTACK_RANGE
        override val attackDamage: Int = CombatConstants.RANGED_MOB_DAMAGE
        override val attackCooldownTotalMs: Int = CombatConstants.RANGED_ATTACK_COOLDOWN_MS
    }
}
