package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class GridPos(
    val x: Int,
    val y: Int,
) {
    fun translate(direction: Direction): GridPos = GridPos(
        x = x + direction.dx,
        y = y + direction.dy,
    )

    fun manhattanDistance(other: GridPos): Int =
        kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y)
}
