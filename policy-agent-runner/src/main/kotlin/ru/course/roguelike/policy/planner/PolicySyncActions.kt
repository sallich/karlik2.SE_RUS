package ru.course.roguelike.policy.planner

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.planner.ToolCallDecision

/** Builds [game_sync] with optional inventory / hotbar fields (MCP schema). */
object PolicySyncActions {
    fun sync(
        sessionId: String,
        yaw: Float,
        forward: Boolean = true,
        deltaMs: Int = 100,
        attack: Boolean = false,
        reload: Boolean = false,
        interact: Boolean = false,
        hotbarSelect: Int? = null,
        hotbarAssign: Int? = null,
        inventoryCycle: Boolean = false,
        inventoryDrop: Boolean = false,
        jump: Boolean = false,
    ): ToolCallDecision = ToolCallDecision(
        tool = "game_sync",
        arguments = buildJsonObject {
            put("sessionId", sessionId)
            put("clientYaw", yaw)
            put("clientPitch", 0f)
            put("forward", forward)
            put("deltaMs", deltaMs)
            put("attack", attack)
            put("reload", reload)
            put("interact", interact)
            put("jump", jump)
            hotbarSelect?.let { put("hotbarSelect", it) }
            hotbarAssign?.let { put("hotbarAssign", it) }
            put("inventoryCycle", inventoryCycle)
            put("inventoryDrop", inventoryDrop)
        }.mapValues { it.value },
    )
}
