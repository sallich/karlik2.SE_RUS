package ru.course.roguelike.policy

import kotlinx.serialization.json.JsonElement
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.McpToolResult
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.mcp.client.GameSessionPort
import ru.course.roguelike.mcp.protocol.McpToolRegistry
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse
import ru.course.roguelike.shared.mcp.McpTool

internal object PolicyIntegrationStack {
    data class Running(val registry: McpToolRegistry)

    fun start(): Running {
        val engine = GameEngine()
        val port = object : GameSessionPort {
            override suspend fun createSession(seed: Long?, twoLevel: Boolean, coopAgent: Boolean): GameSnapshot =
                engine.createSession(seed, twoLevel, coopAgent)

            override suspend fun observe(sessionId: String): GameSnapshot =
                engine.getSnapshot(sessionId) ?: error("Session not found: $sessionId")

            override suspend fun applyAction(sessionId: String, action: String, actor: String): PlayerActionResponse =
                engine.applyAction(sessionId, action, actor)?.response
                    ?: PlayerActionResponse(accepted = false, message = "Session not found")

            override suspend fun sync(sessionId: String, input: InputSyncRequest, actor: String): PlayerActionResponse =
                engine.syncInput(sessionId, input.copy(actor = actor))?.response
                    ?: PlayerActionResponse(accepted = false, message = "Session not found")
        }
        return Running(McpToolRegistry(port))
    }
}

internal class InProcessPolicyMcpClient(
    private val registry: McpToolRegistry,
) : McpClient {
    override suspend fun callTool(name: String, arguments: Map<String, JsonElement>): McpToolResult {
        val response = registry.invoke(name, arguments)
        return McpToolResult(
            text = response.content.firstOrNull()?.text ?: "",
            isError = response.isError,
        )
    }

    override suspend fun getTools(): List<McpTool> =
        registry.descriptors().map { descriptor ->
            McpTool(
                name = descriptor.name,
                description = descriptor.description,
                inputSchema = descriptor.inputSchema,
            )
        }

    override fun close() = Unit
}
