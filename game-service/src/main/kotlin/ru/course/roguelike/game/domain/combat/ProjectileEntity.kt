package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.WorldVertical
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class ProjectileEntity(
    val id: Long,
    var x: Float,
    var y: Float,
    var z: Float,
    val velocityX: Float,
    val velocityY: Float,
    val velocityZ: Float,
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
        z = z,
    )

    companion object {
        fun fromMob(
            mob: MobEntity,
            targetX: Float,
            targetY: Float,
            targetZ: Float,
            id: Long,
        ): ProjectileEntity {
            val originZ = mob.hitCenterZ()
            val dx = targetX - mob.x
            val dy = targetY - mob.y
            val dz = targetZ - originZ
            val dist = hypot(
                hypot(dx.toDouble(), dy.toDouble()).toFloat().toDouble(),
                dz.toDouble(),
            ).toFloat().coerceAtLeast(0.001f)
            val speed = CombatConstants.PROJECTILE_SPEED
            return ProjectileEntity(
                id = id,
                x = mob.x,
                y = mob.y,
                z = originZ,
                velocityX = dx / dist * speed,
                velocityY = dy / dist * speed,
                velocityZ = dz / dist * speed,
                damage = mob.attackDamage,
                ownerMobId = mob.id,
            )
        }

        fun fromPlayer(pose: PlayerPose, id: Long, damage: Int, yawOffset: Float = 0f): ProjectileEntity {
            val speed = CombatConstants.PLAYER_PROJECTILE_SPEED
            val yaw = pose.yaw + yawOffset
            val pitch = pose.pitch
            val horizontal = cos(pitch) * speed
            val spawnZ = pose.height + WorldVertical.EYE_HEIGHT
            return ProjectileEntity(
                id = id,
                x = pose.x,
                y = pose.y,
                z = spawnZ,
                velocityX = cos(yaw) * horizontal,
                velocityY = sin(yaw) * horizontal,
                velocityZ = sin(pitch) * speed,
                damage = damage,
                ownerMobId = null,
            )
        }
    }
}
