package ru.course.roguelike.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import ru.course.roguelike.mcp.JsonRpcError
import ru.course.roguelike.mcp.JsonRpcRequest
import ru.course.roguelike.mcp.JsonRpcResponse
import ru.course.roguelike.mcp.McpContentBlock
import ru.course.roguelike.mcp.McpToolCallResponse
import ru.course.roguelike.mcp.McpToolDescriptor
import ru.course.roguelike.mcp.api.stubToolsList
import ru.course.roguelike.mcp.client.GameServiceClient

class McpToolRegistry(
    private val gameClient: GameServiceClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun descriptors(): List<McpToolDescriptor> = stubToolsList()

    suspend fun invoke(name: String, arguments: Map<String, JsonElement>): McpToolCallResponse =
        when (name) {
            "game_new_session" -> {
                val seed = arguments["seed"]?.jsonPrimitive?.longOrNull
                val session = gameClient.createSession(seed)
                textResponse(json.encodeToString(session))
            }
            "game_observe" -> {
                val sessionId = arguments["sessionId"]?.jsonPrimitive?.content
                    ?: return errorResponse("sessionId is required")
                val snapshot = gameClient.observe(sessionId)
                textResponse(json.encodeToString(snapshot))
            }
            "game_act" -> {
                val sessionId = arguments["sessionId"]?.jsonPrimitive?.content
                    ?: return errorResponse("sessionId is required")
                val action = arguments["action"]?.jsonPrimitive?.content
                    ?: return errorResponse("action is required")
                val result = gameClient.applyAction(sessionId, action)
                textResponse(json.encodeToString(result))
            }
            else -> errorResponse("Unknown tool: $name")
        }

    suspend fun handleJsonRpc(request: JsonRpcRequest): JsonRpcResponse = when (request.method) {
        "tools/list" -> JsonRpcResponse(
            id = request.id,
            result = buildJsonObject {
                put("tools", json.encodeToJsonElement(descriptors()))
            },
        )
        "tools/call" -> handleToolsCall(request)
        else -> rpcError(request.id, -32601, "Method not found: ${request.method}")
    }

    private suspend fun handleToolsCall(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params?.jsonObject
            ?: return rpcError(request.id, -32602, "Invalid params")
        val name = params["name"]?.jsonPrimitive?.content
            ?: return rpcError(request.id, -32602, "name is required")
        val args = params["arguments"]?.jsonObject?.toMap() ?: emptyMap()
        val toolResult = invoke(name, args)
        return JsonRpcResponse(
            id = request.id,
            result = buildJsonObject {
                put(
                    "content",
                    json.parseToJsonElement(json.encodeToString(toolResult.content)),
                )
                put("isError", toolResult.isError)
            },
        )
    }

    private fun textResponse(text: String) = McpToolCallResponse(
        content = listOf(McpContentBlock(text = text)),
        isError = false,
    )

    private fun errorResponse(message: String) = McpToolCallResponse(
        content = listOf(McpContentBlock(text = message)),
        isError = true,
    )

    private fun rpcError(id: JsonElement?, code: Int, message: String) = JsonRpcResponse(
        id = id,
        error = JsonRpcError(code = code, message = message),
    )
}
