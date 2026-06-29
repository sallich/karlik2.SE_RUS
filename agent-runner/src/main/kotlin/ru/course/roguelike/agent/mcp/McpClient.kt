package ru.course.roguelike.agent.mcp

import kotlinx.serialization.json.JsonElement
import ru.course.roguelike.shared.mcp.McpTool

data class McpToolResult(
    val text: String,
    val isError: Boolean,
)

interface McpClient {
    suspend fun callTool(
        name: String,
        arguments: Map<String, JsonElement>,
    ): McpToolResult

    suspend fun getTools(): List<McpTool>

    fun close()
}
