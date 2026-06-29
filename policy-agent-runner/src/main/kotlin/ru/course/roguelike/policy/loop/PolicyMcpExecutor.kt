package ru.course.roguelike.policy.loop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.McpGameActionResult
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import kotlin.math.abs

/** Executes MCP tool calls — only transport to mcp-server, no game-service URL. */
class PolicyMcpExecutor(
    private val mcp: McpClient,
    private val sessionId: String,
    private val toolLog: MutableList<String>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(PolicyMcpExecutor::class.java)

    suspend fun observe(): GameSnapshot? {
        val args = mapOf("sessionId" to JsonPrimitive(sessionId))
        val result = mcp.callTool("game_observe", args)
        toolLog.add("game_observe -> error=${result.isError}")
        if (result.isError) return null
        return json.decodeFromString<GameSnapshot>(result.text)
    }

    suspend fun calibrateIfNeeded(snapshot: GameSnapshot): GameSnapshot {
        val yaw = snapshot.agent?.pose?.yaw ?: snapshot.player.pose.yaw
        if (abs(yaw) < 0.01f) return snapshot
        log.debug("Calibrating yaw={}", yaw)
        val args = mapOf(
            "sessionId" to JsonPrimitive(sessionId),
            "yawDelta" to JsonPrimitive(-yaw),
            "deltaMS" to JsonPrimitive(50),
        )
        val result = mcp.callTool("game_sync", args)
        toolLog.add("game_sync calibrate -> error=${result.isError}")
        return observe() ?: snapshot
    }

    suspend fun execute(decision: ToolCallDecision): ExecuteOutcome {
        val args = decision.arguments.toMutableMap().apply {
            if (!containsKey("sessionId")) this["sessionId"] = JsonPrimitive(sessionId)
        }
        val result = mcp.callTool(decision.tool, args)
        toolLog.add("${decision.tool} -> error=${result.isError}")
        log.info("policy tool={} err={}", decision.tool, result.isError)

        if (result.isError) {
            return ExecuteOutcome(snapshot = null, isError = true, errorText = result.text)
        }

        val snapshot = if (decision.tool == "game_act" || decision.tool == "game_sync") {
            json.decodeFromString<McpGameActionResult>(result.text).snapshot
        } else {
            null
        }
        return ExecuteOutcome(snapshot = snapshot, isError = false, errorText = null)
    }

    data class ExecuteOutcome(
        val snapshot: GameSnapshot?,
        val isError: Boolean,
        val errorText: String?,
    )
}

suspend fun createSession(mcp: McpClient, seed: Long?, toolLog: MutableList<String>): String? {
    val json = Json { ignoreUnknownKeys = true }
    val args = buildMap {
        seed?.let { put("seed", JsonPrimitive(it)) }
        put("twoLevel", JsonPrimitive(false))
    }
    val result = mcp.callTool("game_new_session", args)
    toolLog.add("game_new_session -> error=${result.isError}")
    if (result.isError) return null
    return json.decodeFromString<GameSnapshot>(result.text).sessionId
}

fun actionLabel(decision: ToolCallDecision): String =
    decision.arguments["action"]?.jsonPrimitive?.content ?: decision.tool
