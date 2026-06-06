package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.protocol.GameActions

@Serializable
enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, 1),
    SOUTH(0, -1),
    EAST(1, 0),
    WEST(-1, 0),
    ;

    companion object {
        fun fromAction(action: String): Direction? = when (action) {
            GameActions.MOVE_NORTH -> NORTH
            GameActions.MOVE_SOUTH -> SOUTH
            GameActions.MOVE_EAST -> EAST
            GameActions.MOVE_WEST -> WEST
            else -> null
        }
    }
}
