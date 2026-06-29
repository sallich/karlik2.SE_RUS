package ru.course.roguelike.client

import ru.course.roguelike.shared.model.PlayerPose

object PoseBlend {
    /** Коэффициент сглаживания за кадр (экспоненциальный). */
    fun frameFactor(deltaSec: Float, ratePerSec: Float): Float =
        (1f - kotlin.math.exp(-deltaSec * ratePerSec)).coerceIn(0f, 1f)

    /** Подтягиваем prediction к ответу сервера без рывка. */
    fun towardPosition(predicted: PlayerPose, authoritative: PlayerPose, factor: Float): PlayerPose {
        val t = factor.coerceIn(0f, 1f)
        return predicted.copy(
            x = predicted.x + (authoritative.x - predicted.x) * t,
            y = predicted.y + (authoritative.y - predicted.y) * t,
        )
    }

    fun toward(predicted: PlayerPose, authoritative: PlayerPose, factor: Float): PlayerPose {
        val t = factor.coerceIn(0f, 1f)
        return PlayerPose(
            x = predicted.x + (authoritative.x - predicted.x) * t,
            y = predicted.y + (authoritative.y - predicted.y) * t,
            yaw = predicted.yaw + angleDelta(predicted.yaw, authoritative.yaw) * t,
            pitch = predicted.pitch + (authoritative.pitch - predicted.pitch) * t,
        )
    }

    fun angleDelta(from: Float, to: Float): Float {
        var d = to - from
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        return d
    }
}
