package ru.course.roguelike.shared.protocol

object GameActions {
    const val MOVE_NORTH = "move_north"
    const val MOVE_SOUTH = "move_south"
    const val MOVE_EAST = "move_east"
    const val MOVE_WEST = "move_west"

    const val MOVE_FORWARD = "move_forward"
    const val TURN_LEFT = "turn_left"
    const val TURN_RIGHT = "turn_right"
    const val INTERACT = "interact"
    const val WAIT = "wait"

    /** Классические grid-ходы (MCP legacy). */
    val MOVEMENT: Set<String> = setOf(MOVE_NORTH, MOVE_SOUTH, MOVE_EAST, MOVE_WEST)

    /** Дискретные FPS-действия агента. */
    val AGENT: Set<String> = setOf(MOVE_FORWARD, TURN_LEFT, TURN_RIGHT, INTERACT, WAIT)

    /** Все действия, доступные через game_act. */
    val ALL: Set<String> = MOVEMENT + AGENT

    const val AGENT_FORWARD_MS = 200
    const val AGENT_TURN_MS = 150
    const val AGENT_WAIT_MS = 100
}
