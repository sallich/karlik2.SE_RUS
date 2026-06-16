package ru.course.roguelike.shared.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * MCP Tool definition as received from tools/list.
 * Matches the JSON-RPC response format.
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

/**
 * Result of calling a tool.
 */
@Serializable
data class McpToolResult(
    val isError: Boolean = false,
    val content: List<McpContent> = emptyList()
)

@Serializable
data class McpContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)