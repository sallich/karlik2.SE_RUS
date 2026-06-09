package ru.course.roguelike.game.domain.ai

import ru.course.roguelike.game.domain.combat.MobEntity
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.MobKind
import kotlin.math.hypot

interface MobBehavior {
    fun decide(context: MobDecisionContext): MobIntent
}

class RusherBehavior : MobBehavior {
    override fun decide(context: MobDecisionContext): MobIntent {
        if (context.distanceToPlayer <= context.mob.attackRange) {
            return MobIntent.AttackPlayer
        }
        return MobIntent.ChasePlayer
    }
}
class ShooterBehavior : MobBehavior {
    override fun decide(context: MobDecisionContext): MobIntent {
        val mob = context.mob
        if (context.distanceToPlayer <= mob.attackRange && mob.attackCooldownMs <= 0) {
            return MobIntent.ShootPlayer
        }
        if (context.distanceToPlayer < CombatConstants.RANGED_MIN_DISTANCE) {
            return MobIntent.KitePlayer
        }
        if (context.distanceToPlayer > mob.attackRange * 0.85f) {
            return MobIntent.ChasePlayer
        }
        return MobIntent.StrafePlayer
    }
}

data class MobDecisionContext(
    val mob: MobEntity,
    val playerX: Float,
    val playerY: Float,
    val distanceToPlayer: Float,
    val playerHp: Int = 0,
)

sealed interface MobIntent {
    data object Idle : MobIntent
    data object ChasePlayer : MobIntent
    data object KitePlayer : MobIntent
    data object StrafePlayer : MobIntent
    data object AttackPlayer : MobIntent
    data object ShootPlayer : MobIntent
}

fun mobBehaviorFor(kind: MobKind): MobBehavior = when (kind) {
    MobKind.MELEE -> RusherBehavior()
    MobKind.RANGED -> ShooterBehavior()
    MobKind.LLM_GUARD -> LlmGuardBehavior()
}

fun MobEntity.distanceTo(playerX: Float, playerY: Float): Float =
    hypot((x - playerX).toDouble(), (y - playerY).toDouble()).toFloat()
