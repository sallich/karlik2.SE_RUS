package ru.course.roguelike.mcp.protocol

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.mcp.McpTool

object McpToolDefinitions {

    val ALL: List<McpTool> = listOf(
        McpTool(
            name = "game_new_session",
            description = "Create a new roguelike session with optional deterministic seed.",
            inputSchema = buildJsonObject {
                put(
                    "type",
                    "object",
                )
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "seed",
                            buildJsonObject {
                                put(
                                    "type",
                                    "integer",
                                )
                                put(
                                    "description",
                                    "Optional RNG seed.",
                                )
                            },
                        )
                        put(
                            "twoLevel",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                                put(
                                    "description",
                                    "Two-level map (default false for agents).",
                                )
                            },
                        )
                    },
                )
                put(
                    "additionalProperties",
                    false,
                )
            },
        ),
        McpTool(
            name = "game_observe",
            description = "Return full GameSnapshot: map tiles, player pose, keys, mobs, phase.",
            inputSchema = buildJsonObject {
                put(
                    "type",
                    "object",
                )
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "sessionId",
                            buildJsonObject {
                                put(
                                    "type",
                                    "string",
                                )
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add("sessionId")
                    },
                )
                put(
                    "additionalProperties",
                    false,
                )
            },
        ),
        McpTool(
            name = "game_sync",
            description = "Apply held FPS input for deltaMs (movement, yawDelta, interact, attack).",
            inputSchema = buildJsonObject {
                put(
                    "type",
                    "object",
                )
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "sessionId",
                            buildJsonObject {
                                put(
                                    "type",
                                    "string",
                                )
                            },
                        )
                        put(
                            "forward",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "backward",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "strafeLeft",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "strafeRight",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "turnLeft",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "turnRight",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "yawDelta",
                            buildJsonObject {
                                put(
                                    "type",
                                    "number",
                                )
                            },
                        )
                        put(
                            "interact",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "attack",
                            buildJsonObject {
                                put(
                                    "type",
                                    "boolean",
                                )
                            },
                        )
                        put(
                            "deltaMs",
                            buildJsonObject {
                                put(
                                    "type",
                                    "integer",
                                )
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add("sessionId")
                    },
                )
                put(
                    "additionalProperties",
                    false,
                )
            },
        ),
        McpTool(
            name = "game_session_summary",
            description = "Compact session state: phase, hp, keys progress, player cell, exit gate.",
            inputSchema = buildJsonObject {
                put(
                    "type",
                    "object",
                )
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "sessionId",
                            buildJsonObject {
                                put(
                                    "type",
                                    "string",
                                )
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add("sessionId")
                    },
                )
                put(
                    "additionalProperties",
                    false,
                )
            },
        ),
        McpTool(
            name = "game_list_actions",
            description = "List all valid action strings for game_act.",
            inputSchema = buildJsonObject {
                put(
                    "type",
                    "object",
                )
                put(
                    "properties",
                    buildJsonObject { },
                )
                put(
                    "additionalProperties",
                    false,
                )
            },
        ),
        McpTool(
            name = "game_act",
            description = "Apply a discrete agent action.",
            inputSchema = buildJsonObject {
                put(
                    "type",
                    "object",
                )
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "sessionId",
                            buildJsonObject {
                                put(
                                    "type",
                                    "string",
                                )
                            },
                        )
                        put(
                            "action",
                            buildJsonObject {
                                put(
                                    "type",
                                    "string",
                                )
                                put(
                                    "enum",
                                    buildJsonArray {
                                        add("interact")
                                        add("wait")
                                        add("move_north")
                                        add("move_south")
                                        add("move_east")
                                        add("move_west")
                                    },
                                )
                            },
                        )
                    },
                )
                put(
                    "required",
                    buildJsonArray {
                        add("sessionId")
                        add("action")
                    },
                )
                put(
                    "additionalProperties",
                    false,
                )
            },
        ),
    )

    val NAMES: Set<String> = ALL.map { it.name }.toSet()
}
