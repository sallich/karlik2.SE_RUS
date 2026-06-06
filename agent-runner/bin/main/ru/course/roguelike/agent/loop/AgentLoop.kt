package ru.course.roguelike.agent.loop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.AgentRunResponse
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.llm.HeuristicDecisionClient
import ru.course.roguelike.agent.llm.LlmClientFactory
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.McpClientFactory
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.SessionPhase

class AgentLoop(
    private val config: AgentConfig,
    private val mcpFactory: (AgentConfig) -> McpClient = McpClientFactory::create,
    private val llmFactory: LlmClientFactory = LlmClientFactory(),
) {
    private val log = LoggerFactory.getLogger(AgentLoop::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(request: AgentRunRequest): AgentRunResponse {
        val budget = minOf(request.maxSteps, config.maxToolCalls)
        val mcp = mcpFactory(config)
        val fallback = HeuristicDecisionClient()
        val llm = llmFactory.create(config, fallback)
        val toolLog = mutableListOf<String>()

        return try {
            val coop = request.sessionId != null
            val actor = if (coop) KeyHuntPlanner.ACTOR_AGENT else KeyHuntPlanner.ACTOR_PLAYER
            val sessionId = request.sessionId ?: createSession(mcp, request.seed, toolLog)
                ?: return failed("Failed to create session", toolLog)

            val outcome = executeSteps(mcp, llm, sessionId, budget, toolLog, actor)
            val success = outcome.finalPhase == SessionPhase.LEVEL_COMPLETE.name
            AgentRunResponse(
                status = if (success) "COMPLETE" else "STOPPED",
                message = if (success) "Level completed" else "Stopped at phase ${outcome.finalPhase}",
                stepsUsed = outcome.steps,
                stepsPlanned = budget,
                sessionId = sessionId,
                finalPhase = outcome.finalPhase,
                success = success,
                toolCallLog = toolLog.toList(),
            )
        } finally {
            mcp.close()
        }
    }

    private suspend fun executeSteps(
        mcp: McpClient,
        llm: ru.course.roguelike.agent.llm.AgentDecisionClient,
        sessionId: String,
        budget: Int,
        toolLog: MutableList<String>,
        actor: String,
    ): StepOutcome {
        var steps = 0
        var finalPhase = SessionPhase.EXPLORATION.name
        while (steps < budget) {
            val snapshot = observe(mcp, sessionId, toolLog) ?: return StepOutcome(steps, finalPhase)
            finalPhase = snapshot.phase
            if (isTerminalPhase(snapshot.phase)) return StepOutcome(steps, finalPhase)
            performToolCall(mcp, llm, sessionId, snapshot, toolLog, steps, actor)
            steps++
        }
        return StepOutcome(steps, finalPhase)
    }

    private suspend fun performToolCall(
        mcp: McpClient,
        llm: ru.course.roguelike.agent.llm.AgentDecisionClient,
        sessionId: String,
        snapshot: GameSnapshot,
        toolLog: MutableList<String>,
        stepIndex: Int,
        actor: String,
    ) {
        val decision = llm.chooseTool(snapshot, sessionId, actor)
        val args = decision.arguments.toMutableMap()
        if (actor == KeyHuntPlanner.ACTOR_AGENT) {
            args["actor"] = JsonPrimitive(KeyHuntPlanner.ACTOR_AGENT)
        }
        val result = mcp.callTool(decision.tool, args)
        toolLog.add("${decision.tool} -> error=${result.isError}")
        log.info("step=$stepIndex tool=${decision.tool} err=${result.isError}")
        if (result.isError) {
            toolLog.add(result.text)
        }
    }

    private fun isTerminalPhase(phase: String): Boolean =
        phase == SessionPhase.LEVEL_COMPLETE.name || phase == SessionPhase.GAME_OVER.name

    private data class StepOutcome(val steps: Int, val finalPhase: String)

    private suspend fun createSession(mcp: McpClient, seed: Long?, log: MutableList<String>): String? {
        val args = buildJsonObject {
            seed?.let { put("seed", it) }
            put("twoLevel", false)
        }.mapValues { it.value }
        val result = mcp.callTool("game_new_session", args)
        log.add("game_new_session -> error=${result.isError}")
        if (result.isError) return null
        return json.decodeFromString<GameSnapshot>(result.text).sessionId
    }

    private suspend fun observe(mcp: McpClient, sessionId: String, log: MutableList<String>): GameSnapshot? {
        val args = mapOf<String, JsonElement>(
            "sessionId" to json.parseToJsonElement("\"$sessionId\""),
        )
        val result = mcp.callTool("game_observe", args)
        log.add("game_observe -> error=${result.isError}")
        if (result.isError) return null
        return json.decodeFromString<GameSnapshot>(result.text)
    }

    private fun failed(message: String, log: List<String>) = AgentRunResponse(
        status = "FAILED",
        message = message,
        toolCallLog = log,
    )
}
