package ru.course.roguelike.shared.combat

import kotlin.math.atan2
import kotlin.math.hypot
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.FpsConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.WorldVertical

/** Расчёт прицеливания по вертикали (pitch) для 3D-попаданий. */
object CombatAim {
    fun eyeZ(pose: PlayerPose): Float = pose.height + WorldVertical.EYE_HEIGHT

    fun mobHitCenterZ(mobZ: Float): Float = mobZ + CombatConstants.MOB_HIT_HALF_HEIGHT

    fun pitchToTarget(pose: PlayerPose, targetX: Float, targetY: Float, targetZ: Float): Float {
        val horizontal = hypot(
            (targetX - pose.x).toDouble(),
            (targetY - pose.y).toDouble(),
        ).toFloat().coerceAtLeast(0.001f)
        val dz = targetZ - eyeZ(pose)
        return atan2(dz.toDouble(), horizontal.toDouble()).toFloat()
            .coerceIn(-FpsConstants.MAX_PITCH, FpsConstants.MAX_PITCH)
    }
}
