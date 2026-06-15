package ru.course.roguelike.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.course.roguelike.mcp.JsonRpcRequest
import ru.course.roguelike.mcp.JsonRpcResponse
import ru.course.roguelike.mcp.McpToolCallResponse
import ru.course.roguelike.mcp.client.GameSessionPort
import ru.course.roguelike.shared.mcp.McpTool
import ru.course.roguelike.shared.protocol.GameActions

class McpToolRegistry(
    private val gameClient: GameSessionPort,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun descriptors(): List<McpTool> = McpToolDefinitions.ALL

    suspend fun invoke(name: String, arguments: Map<String, JsonElement>): McpToolCallResponse =
        when (name) {
            "game_new_session" -> invokeNewSession(arguments)
            "game_observe" -> invokeObserve(arguments)
            "game_act" -> invokeAct(arguments)
            "game_sync" -> invokeSync(arguments)
            "game_session_summary" -> invokeSummary(arguments)
            "game_list_actions" -> McpToolResponses.text(json.encodeToString(GameActions.ALL.sorted()))
            else -> McpToolResponses.error("Unknown tool: $name")
        }

    suspend fun handleJsonRpc(request: JsonRpcRequest): JsonRpcResponse = when (request.method) {
        "tools/list" -> JsonRpcResponse(
            id = request.id,
            result = buildJsonObject {
                put("tools", json.encodeToJsonElement(descriptors()))
            },
        )
        "tools/call" -> handleToolsCall(request)
        else -> McpToolResponses.rpcError(request.id, -32601, "Method not found: ${request.method}")
    }

    private suspend fun invokeNewSession(arguments: Map<String, JsonElement>): McpToolCallResponse {
        val seed = McpArgumentParser.optionalLong(arguments, "seed")
        val twoLevel = McpArgumentParser.optionalBoolean(arguments, "twoLevel", default = false)
        val coopAgent = McpArgumentParser.optionalBoolean(arguments, "coopAgent", default = false)
        return runTool("Failed to create session") {
            val session = gameClient.createSession(seed, twoLevel, coopAgent)
            json.encodeToString(session)
        }
    }

    private suspend fun invokeObserve(arguments: Map<String, JsonElement>): McpToolCallResponse {
        val sessionId = McpArgumentParser.requireString(arguments, "sessionId")
            ?: return McpToolResponses.error("sessionId is required and must be a non-empty string")
        return runTool("Failed to observe session") {
            json.encodeToString(gameClient.observe(sessionId))
        }
    }

    private suspend fun invokeAct(arguments: Map<String, JsonElement>): McpToolCallResponse {
        val sessionId = McpArgumentParser.requireString(arguments, "sessionId")
            ?: return McpToolResponses.error("sessionId is required and must be a non-empty string")
        val action = McpArgumentParser.requireString(arguments, "action")
            ?: return McpToolResponses.error("action is required and must be a non-empty string")
        if (action !in GameActions.ALL) {
            return McpToolResponses.error(
                "Invalid action '$action'. Use game_list_actions for allowed values.",
            )
        }
        return runTool("Failed to apply action") {
            val actor = McpArgumentParser.optionalActor(arguments)
            json.encodeToString(gameClient.applyAction(sessionId, action, actor))
        }
    }

    private suspend fun invokeSync(arguments: Map<String, JsonElement>): McpToolCallResponse {
        val sessionId = McpArgumentParser.requireString(arguments, "sessionId")
            ?: return McpToolResponses.error("sessionId is required and must be a non-empty string")
        val input = McpArgumentParser.parseSyncInput(arguments)
        return runTool("Failed to sync session") {
            json.encodeToString(gameClient.sync(sessionId, input, input.actor))
        }
    }

    private suspend fun invokeSummary(arguments: Map<String, JsonElement>): McpToolCallResponse {
        val sessionId = McpArgumentParser.requireString(arguments, "sessionId")
            ?: return McpToolResponses.error("sessionId is required and must be a non-empty string")
        return runTool("Failed to build session summary") {
            val snapshot = gameClient.observe(sessionId)
            json.encodeToString(SessionSummaryBuilder.fromSnapshot(snapshot))
        }
    }

    private suspend fun handleToolsCall(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params?.jsonObject
            ?: return McpToolResponses.rpcError(request.id, -32602, "Invalid params")
        val name = params["name"]?.jsonPrimitive?.content
            ?: return McpToolResponses.rpcError(request.id, -32602, "name is required")
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

    private suspend fun runTool(prefix: String, block: suspend () -> String): McpToolCallResponse =
        runCatching { block() }
            .fold(
                onSuccess = { McpToolResponses.text(it) },
                onFailure = { McpToolResponses.error("$prefix: ${it.message}") },
            )
}
