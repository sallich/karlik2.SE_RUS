package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.WorldVertical

/** Вертикальная анимация лифта (общая для сервера и клиента). */
object ElevatorPhysics {
    /** Высота длинного прыжка над колоннами на том же ярусе. */
    const val PEAK_HEIGHT = WorldVertical.FLOOR_STEP

    const val LIFT_SPEED = 2.6f

    data class TickResult(
        val phase: ElevatorPhase,
        val height: Float,
        val verticalVelocity: Float,
    )

    fun tick(phase: ElevatorPhase, height: Float, deltaMs: Int): TickResult {
        val dt = deltaMs.coerceIn(1, 200) / 1000f
        return when (phase) {
            ElevatorPhase.ASCENDING -> {
                val next = height + LIFT_SPEED * dt
                if (next >= PEAK_HEIGHT) {
                    TickResult(
                        phase = ElevatorPhase.DESCENDING,
                        height = PEAK_HEIGHT,
                        verticalVelocity = -LIFT_SPEED,
                    )
                } else {
                    TickResult(phase, next, LIFT_SPEED)
                }
            }
            ElevatorPhase.DESCENDING -> {
                val next = height - LIFT_SPEED * dt
                if (next <= 0f) {
                    TickResult(ElevatorPhase.IDLE, 0f, 0f)
                } else {
                    TickResult(phase, next, -LIFT_SPEED)
                }
            }
            ElevatorPhase.IDLE -> TickResult(phase, height.coerceAtLeast(0f), 0f)
        }
    }
}
