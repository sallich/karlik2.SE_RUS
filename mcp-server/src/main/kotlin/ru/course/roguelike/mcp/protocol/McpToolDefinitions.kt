package ru.course.roguelike.mcp.protocol

import ru.course.roguelike.mcp.McpToolDescriptor

object McpToolDefinitions {
    val ALL: List<McpToolDescriptor> = listOf(
        McpToolDescriptor(
            name = "game_new_session",
            description = "Create a new roguelike session with optional deterministic seed.",
        ),
        McpToolDescriptor(
            name = "game_observe",
            description = "Return full GameSnapshot: map tiles, player pose, keys, mobs, phase.",
        ),
        McpToolDescriptor(
            name = "game_act",
            description = "Apply a discrete agent action (move_forward, turn_left, interact, wait, move_*).",
        ),
        McpToolDescriptor(
            name = "game_sync",
            description = "Apply held FPS input for deltaMs (movement, yawDelta, interact, attack).",
        ),
        McpToolDescriptor(
            name = "game_session_summary",
            description = "Compact session state: phase, hp, keys progress, player cell, exit gate.",
        ),
        McpToolDescriptor(
            name = "game_list_actions",
            description = "List all valid action strings for game_act.",
        ),
    )

    val NAMES: Set<String> = ALL.map { it.name }.toSet()
}
