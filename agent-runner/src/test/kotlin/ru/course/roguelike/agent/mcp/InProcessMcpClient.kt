package ru.course.roguelike.agent.mcp

import kotlinx.serialization.json.JsonElement
import ru.course.roguelike.mcp.protocol.McpToolRegistry
import ru.course.roguelike.shared.mcp.McpTool

/** In-process MCP for fast integration tests (same tool registry as stdio/HTTP). */
class InProcessMcpClient(
    private val registry: McpToolRegistry,
) : McpClient {
    override suspend fun callTool(
        name: String,
        arguments: Map<String, JsonElement>,
    ): McpToolResult {
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
