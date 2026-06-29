package ru.course.roguelike.mcp.client

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse

/** Абстракция над game-service для MCP tools (HTTP или in-process). */
interface GameSessionPort {
    suspend fun createSession(seed: Long?, twoLevel: Boolean = false, coopAgent: Boolean = false): GameSnapshot
    suspend fun observe(sessionId: String): GameSnapshot
    suspend fun applyAction(sessionId: String, action: String, actor: String = "player"): PlayerActionResponse
    suspend fun sync(sessionId: String, input: InputSyncRequest, actor: String = "player"): PlayerActionResponse
}
