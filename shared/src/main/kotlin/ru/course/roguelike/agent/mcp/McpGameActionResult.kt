package ru.course.roguelike.agent.mcp

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerActionResponse

@Serializable
data class GameActResponse(
    val accepted: Boolean,
    val message: String? = null,
    val snapshot: GameSnapshot,
)

/** JSON from game_act / game_sync via MCP (ActionResult from game-service). */
@Serializable
data class McpGameActionResult(
    val response: PlayerActionResponse? = null,
    val snapshot: GameSnapshot,
    val accepted: Boolean? = null,
    val message: String? = null,
) {
    fun toGameActResponse(): GameActResponse = GameActResponse(
        accepted = response?.accepted ?: accepted ?: true,
        message = response?.message ?: message,
        snapshot = snapshot,
    )
}
