package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.MobBehavior
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.MobKind

/**
 * Базовая сущность моба. Конкретные типы задают скорость, дистанцию и урон.
 */
@Suppress("LongParameterList")
sealed class MobEntity(
    val id: Long,
    var x: Float,
    var y: Float,
    var hp: Int,
    val maxHp: Int,
    val kind: MobKind,
    val behavior: MobBehavior,
    /** Комната, в которой моб агрессирует; вне неё — не преследует игрока. */
    val aggroRoom: Room,
    /** Целевая комната при подкреплении (моб идёт по лабиринту, если игрок ещё не там). */
    var reinforceTarget: Room? = null,
    var attackCooldownMs: Int = 0,
    /** Высота над полом яруса; летающие мобы парят выше колонн. */
    var z: Float = 0f,
) {
    abstract val moveSpeed: Float
    abstract val attackRange: Float
    abstract val attackDamage: Int
    abstract val attackCooldownTotalMs: Int

    val alive: Boolean get() = hp > 0
    val isFlying: Boolean get() = z > 0.01f

    fun hitCenterZ(): Float = z + CombatConstants.MOB_HIT_HALF_HEIGHT

    fun toSnapshot() = ru.course.roguelike.shared.dto.MobSnapshot(
        id = id,
        kind = kind,
        x = x,
        y = y,
        hp = hp,
        maxHp = maxHp,
        z = z,
    )

    class MeleeMob(
        id: Long,
        x: Float,
        y: Float,
        behavior: MobBehavior,
        aggroRoom: Room,
    ) : MobEntity(
        id = id,
        x = x,
        y = y,
        hp = CombatConstants.MELEE_MOB_HP,
        maxHp = CombatConstants.MELEE_MOB_HP,
        kind = MobKind.MELEE,
        behavior = behavior,
        aggroRoom = aggroRoom,
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
        aggroRoom: Room,
    ) : MobEntity(
        id = id,
        x = x,
        y = y,
        hp = CombatConstants.RANGED_MOB_HP,
        maxHp = CombatConstants.RANGED_MOB_HP,
        kind = MobKind.RANGED,
        behavior = behavior,
        aggroRoom = aggroRoom,
        z = CombatConstants.FLYING_MOB_Z,
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
        aggroRoom: Room,
    ) : MobEntity(
        id = id,
        x = x,
        y = y,
        hp = CombatConstants.RANGED_MOB_HP,
        maxHp = CombatConstants.RANGED_MOB_HP,
        kind = MobKind.LLM_GUARD,
        behavior = behavior,
        aggroRoom = aggroRoom,
        z = CombatConstants.FLYING_MOB_Z,
    ) {
        override val moveSpeed: Float = CombatConstants.RANGED_MOVE_SPEED
        override val attackRange: Float = CombatConstants.RANGED_ATTACK_RANGE
        override val attackDamage: Int = CombatConstants.RANGED_MOB_DAMAGE
        override val attackCooldownTotalMs: Int = CombatConstants.RANGED_ATTACK_COOLDOWN_MS
    }
}
