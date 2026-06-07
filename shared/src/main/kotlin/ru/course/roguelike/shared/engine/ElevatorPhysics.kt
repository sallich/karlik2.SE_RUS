package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.WorldVertical

/** Вертикальная анимация лифта (общая для сервера и клиента). */
object ElevatorPhysics {
    /** Высота подъёма над текущим ярусом — один шаг между этажами. */
    const val PEAK_HEIGHT = WorldVertical.FLOOR_STEP

    const val LIFT_SPEED = 2.6f

    data class TickResult(
        val phase: ElevatorPhase,
        val height: Float,
        val verticalVelocity: Float,
        val levelSwitch: Boolean,
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
                        levelSwitch = true,
                    )
                } else {
                    TickResult(phase, next, LIFT_SPEED, levelSwitch = false)
                }
            }
            ElevatorPhase.DESCENDING -> {
                val next = height - LIFT_SPEED * dt
                if (next <= 0f) {
                    TickResult(ElevatorPhase.IDLE, 0f, 0f, levelSwitch = false)
                } else {
                    TickResult(phase, next, -LIFT_SPEED, levelSwitch = false)
                }
            }
            ElevatorPhase.IDLE -> TickResult(phase, height.coerceAtLeast(0f), 0f, levelSwitch = false)
        }
    }
}
