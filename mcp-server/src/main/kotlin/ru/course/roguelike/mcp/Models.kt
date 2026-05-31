package ru.course.roguelike.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val gameService: String,
)

@Serializable
data class McpToolDescriptor(
    val name: String,
    val description: String,
)

@Serializable
data class McpToolsListResponse(
    val tools: List<McpToolDescriptor>,
)

@Serializable
data class McpToolCallRequest(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class McpToolCallResponse(
    val content: List<McpContentBlock>,
    val isError: Boolean = false,
)

@Serializable
data class McpContentBlock(
    val type: String = "text",
    val text: String,
)

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
)
