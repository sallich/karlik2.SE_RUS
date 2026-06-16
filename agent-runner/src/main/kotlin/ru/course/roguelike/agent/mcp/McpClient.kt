package ru.course.roguelike.agent.mcp

import kotlinx.serialization.json.JsonElement

data class McpToolResult(
    val text: String,
    val isError: Boolean,
)

interface McpClient {
    suspend fun callTool(name: String, arguments: Map<String, JsonElement>): McpToolResult
    fun close()
}
