package ru.course.roguelike.mcp.protocol

import kotlinx.serialization.json.JsonElement
import ru.course.roguelike.mcp.JsonRpcError
import ru.course.roguelike.mcp.JsonRpcResponse
import ru.course.roguelike.mcp.McpContentBlock
import ru.course.roguelike.mcp.McpToolCallResponse

internal object McpToolResponses {
    fun text(text: String) = McpToolCallResponse(
        content = listOf(McpContentBlock(text = text)),
        isError = false,
    )

    fun error(message: String) = McpToolCallResponse(
        content = listOf(McpContentBlock(text = message)),
        isError = true,
    )

    fun rpcError(id: JsonElement?, code: Int, message: String) = JsonRpcResponse(
        id = id,
        error = JsonRpcError(code = code, message = message),
    )
}
