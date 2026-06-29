package ru.course.roguelike.shared.engine

/** Состояние анимации лифта между ярусами локации. */
enum class ElevatorPhase {
    IDLE,
    ASCENDING,
    DESCENDING,
}
