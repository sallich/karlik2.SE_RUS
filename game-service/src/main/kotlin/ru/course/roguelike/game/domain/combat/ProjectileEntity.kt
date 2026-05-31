package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class ProjectileEntity(
    val id: Long,
    var x: Float,
    var y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val damage: Int,
    /** null — снаряд игрока. */
    val ownerMobId: Long?,
) {
    val fromPlayer: Boolean get() = ownerMobId == null

    fun toSnapshot() = ru.course.roguelike.shared.dto.ProjectileSnapshot(
        id = id,
        x = x,
        y = y,
        fromPlayer = fromPlayer,
    )

    companion object {
        fun fromMob(mob: MobEntity, targetX: Float, targetY: Float, id: Long): ProjectileEntity {
            val dx = targetX - mob.x
            val dy = targetY - mob.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
            val speed = CombatConstants.PROJECTILE_SPEED
            return ProjectileEntity(
                id = id,
                x = mob.x,
                y = mob.y,
                velocityX = dx / dist * speed,
                velocityY = dy / dist * speed,
                damage = mob.attackDamage,
                ownerMobId = mob.id,
            )
        }

        fun fromPlayer(pose: PlayerPose, id: Long): ProjectileEntity {
            val speed = CombatConstants.PLAYER_PROJECTILE_SPEED
            return ProjectileEntity(
                id = id,
                x = pose.x,
                y = pose.y,
                velocityX = cos(pose.yaw) * speed,
                velocityY = sin(pose.yaw) * speed,
                damage = CombatConstants.PLAYER_ATTACK_DAMAGE,
                ownerMobId = null,
            )
        }
    }
}
