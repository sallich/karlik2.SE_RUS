package ru.course.roguelike.agent.mcp

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.mcp.McpTool
import ru.course.roguelike.shared.protocol.GameActions

object AgentMcpTools {
    private val GRID_ACTIONS = listOf(
        GameActions.MOVE_NORTH,
        GameActions.MOVE_SOUTH,
        GameActions.MOVE_EAST,
        GameActions.MOVE_WEST,
        GameActions.TURN_LEFT,
        GameActions.TURN_RIGHT,
        GameActions.INTERACT,
        GameActions.WAIT,
    )

    fun forGameAgent(): List<McpTool> = listOf(
        gameActTool(),
        gameSyncTool(),
    )

    private fun gameActTool(): McpTool = McpTool(
        name = "game_act",
        description = "Grid step, turn, interact (same as player key E), or wait. Use for exploration when no nearby mobs.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("required", buildJsonArray {
                add("sessionId")
                add("action")
            })
            put("properties", buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("action", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "move_* = one cell; turn_* = rotate; interact = key E on door D / key K / exit; wait = skip",
                    )
                    put("enum", buildJsonArray {
                        GRID_ACTIONS.forEach { add(it) }
                    })
                })
            })
            put("additionalProperties", false)
        },
    )

    private fun gameSyncTool(): McpTool = McpTool(
        name = "game_sync",
        description = "Aim and shoot, reload, or move with continuous input. Use when mobs are nearby.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("required", buildJsonArray { add("sessionId") })
            put("properties", buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("clientYaw", buildJsonObject {
                    put("type", "number")
                    put("description", "Aim direction in radians (required for attack)")
                })
                put("clientPitch", buildJsonObject {
                    put("type", "number")
                    put(
                        "description",
                        "Pitch in radians; aim UP (positive) at flying R/G mobs, use prompt value",
                    )
                })
                put("attack", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Fire equipped weapon")
                })
                put("reload", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Reload from inventory when out of ammo")
                })
                put("hotbarSelect", buildJsonObject {
                    put("type", "integer")
                    put("description", "Equip weapon from hotbar slot 1 or 2")
                })
                put("hotbarAssign", buildJsonObject {
                    put("type", "integer")
                    put("description", "Assign next inventory weapon to hotbar slot 1 or 2")
                })
                put("inventoryCycle", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Cycle selected inventory item (Tab+Q)")
                })
                put("inventoryDrop", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Drop selected inventory item (Tab+F)")
                })
                put("forward", buildJsonObject { put("type", "boolean") })
                put("jump", buildJsonObject {
                    put("type", "boolean")
                    put(
                        "description",
                        "Jump (Shift): clear low COLUMN obstacles or chain with forward movement",
                    )
                })
                put("backward", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Strafe back while shooting melee mobs")
                })
                put("deltaMs", buildJsonObject {
                    put("type", "integer")
                    put("description", "Simulated input duration in ms (50-200)")
                })
            })
            put("additionalProperties", false)
        },
    )
}
