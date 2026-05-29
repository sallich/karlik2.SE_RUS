package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPose(
    val x: Float,
    val y: Float,
    /** Горизонтальный угол (радианы), 0 = вдоль +X. */
    val yaw: Float,
    val pitch: Float = 0f,
) {
    fun withPosition(nx: Float, ny: Float) = copy(x = nx, y = ny)
    fun withAngles(nyaw: Float, npitch: Float) = copy(yaw = nyaw, pitch = npitch)

    companion object {
        fun fromGridCell(cell: GridPos) = PlayerPose(
            x = cell.x + 0.5f,
            y = cell.y + 0.5f,
            yaw = 0f,
            pitch = 0f,
        )
    }
}

object FpsConstants {
    const val MOVE_SPEED = 3.5f
    const val TURN_SPEED = 2.8f
    const val PITCH_SPEED = 1.4f
    const val MAX_PITCH = 1.1f
    const val PLAYER_RADIUS = 0.2f
    /** Зазор от грани стены после остановки (предотвращает дрожание на грани float). */
    const val WALL_SKIN = 0.002f
    /** Минимальная дистанция луча до стены — совпадает с «упором» круга игрока. */
    const val COLLISION_MIN_RAY_DIST = PLAYER_RADIUS + WALL_SKIN
    /** Движение по осям X затем Y — скольжение вдоль стен (см. FpsMovementSystem). */
    const val SLIDE_ALONG_WALL = true
    /** Подшаг симуляции: поворот и W на каждом шаге (камера = направление хода). */
    const val MOVEMENT_SUBSTEP_MS = 16
    const val DEFAULT_FOV_RADIANS = 0.82f
    const val SYNC_HZ = 30
    const val SYNC_INTERVAL_SEC = 1f / SYNC_HZ
    /** Доля подтяжки к серверу за один ответ sync (только позиция, не yaw). */
    const val SYNC_POSITION_BLEND = 0.35f
    /** Расхождение (тайлы): мягкая коррекция; ниже — игнор (шум симуляции / сети). */
    const val SYNC_POSITION_CORRECT_MIN = 0.06f
    /** Расхождение (тайлы): жёсткий snap (рассинхрон / лаг). */
    const val SYNC_POSITION_HARD_SNAP = 0.55f
    /** Мышь вправо → взгляд вправо (стандартный FPS). */
    const val MOUSE_YAW_SIGN = -1f
}
