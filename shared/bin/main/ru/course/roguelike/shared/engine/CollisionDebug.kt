package ru.course.roguelike.shared.engine

import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose

data class CollisionDebug(
    val startX: Float,
    val startY: Float,
    val requestedDx: Float,
    val requestedDy: Float,
    val actualDx: Float,
    val actualDy: Float,
    val hitCells: List<GridPos>,
    val blocked: Boolean,
    val sweepFraction: Float,
    val moveYaw: Float = 0f,
    val lookYaw: Float = 0f,
)

data class MovementOutcome(
    val pose: PlayerPose,
    val debug: CollisionDebug,
)
