package ru.course.roguelike.shared.protocol

object GameActions {
    const val MOVE_NORTH = "move_north"
    const val MOVE_SOUTH = "move_south"
    const val MOVE_EAST = "move_east"
    const val MOVE_WEST = "move_west"

    val MOVEMENT: Set<String> = setOf(MOVE_NORTH, MOVE_SOUTH, MOVE_EAST, MOVE_WEST)
}
