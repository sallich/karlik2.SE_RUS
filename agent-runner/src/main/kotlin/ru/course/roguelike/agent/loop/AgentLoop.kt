package ru.course.roguelike.agent.loop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.AgentRunResponse
import ru.course.roguelike.agent.AssistantMessage
import ru.course.roguelike.agent.FunctionCall
import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.SystemMessage
import ru.course.roguelike.agent.ToolCall
import ru.course.roguelike.agent.ToolCallList
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.llm.AgentDecisionClient
import ru.course.roguelike.agent.llm.HeuristicDecisionClient
import ru.course.roguelike.agent.llm.LlmClientFactory
import ru.course.roguelike.agent.llm.PromptBuilder.buildSystemPromptInLoop
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.McpClientFactory
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import ru.course.roguelike.shared.model.SessionPhase

class AgentLoop(
    private val config: AgentConfig,
    private val mcpFactory: (AgentConfig) -> McpClient = McpClientFactory::create,
    private val llmFactory: LlmClientFactory = LlmClientFactory(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(AgentLoop::class.java)
    private val conversation = mutableListOf<LLMMessage>()
    private val lastPositions = ArrayDeque<Pair<Int, Int>>(4)
    private val lastActionKeys = ArrayDeque<String>(5)

    suspend fun run(request: AgentRunRequest): AgentRunResponse {
        val budget = minOf(request.maxSteps, config.maxToolCalls)
        val mcp = mcpFactory(config)
        val fallback = HeuristicDecisionClient()
        val llm = llmFactory.create(config, fallback)
        val toolLog = mutableListOf<String>()
        lastPositions.clear()
        lastActionKeys.clear()
        conversation.clear()

        return try {
            val coop = request.sessionId != null
            val actor = if (coop) KeyHuntPlanner.ACTOR_AGENT else KeyHuntPlanner.ACTOR_PLAYER
            val sessionId =
                request.sessionId ?: createSession(mcp, request.seed, toolLog)
                    ?: return failed("Failed to create session", toolLog)

            val outcome = executeSteps(mcp, llm, sessionId, budget, toolLog, actor, fallback)
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
                finalHp = outcome.finalHp,
                tokensUsed = estimateConversationTokens(conversation),
            )
        } finally {
            mcp.close()
        }
    }

    private suspend fun executeSteps(
        mcp: McpClient,
        llm: AgentDecisionClient,
        sessionId: String,
        budget: Int,
        toolLog: MutableList<String>,
        actor: String,
        fallback: AgentDecisionClient,
    ): StepOutcome {
        var steps = 0
        var finalPhase = SessionPhase.EXPLORATION.name
        var finalHp: Int? = null

        val tools = getTools(mcp)
        log.debug("Tools: {}", tools)

        val toolExecutor =
            ToolExecutor(
                mcp,
                fallback,
                conversation,
                lastPositions,
                lastActionKeys,
                tools,
                sessionId,
                actor,
                toolLog,
                budget,
            )

        conversation.add(SystemMessage(buildSystemPromptInLoop(sessionId)))

        while (steps < budget) {
            val snapshot = observe(mcp, sessionId, toolLog) ?: return StepOutcome(steps, finalPhase, finalHp)
            finalPhase = snapshot.phase
            finalHp = snapshot.agent?.hp ?: snapshot.player.hp
            if (isTerminalPhase(snapshot.phase)) return StepOutcome(steps, finalPhase, finalHp)

            calibrateIfNeeded(mcp, sessionId, snapshot, toolLog)

            val decisions = llm.chooseTool(snapshot, sessionId, conversation, tools, actor)
            if (decisions.isEmpty()) {
                log.info("Model returned final answer (no tool call), stopping agent")
                break
            }

            log.debug("Model returned {} tool call(s): {}", decisions.size, decisions)

            recordAssistantMessage(decisions)

            val outcome = toolExecutor.executeAll(decisions, steps, snapshot)

            steps += outcome.stepsUsed
            finalPhase = outcome.snapshot.phase
            finalHp = outcome.snapshot.agent?.hp ?: outcome.snapshot.player.hp
            if (outcome.shouldStop) return StepOutcome(steps, finalPhase, finalHp)
        }
        return StepOutcome(steps, finalPhase, finalHp)
    }

    private suspend fun getTools(mcp: McpClient): List<McpTool> {
        val allTools = mcp.getTools()
        val allowedToolNames = config.allowedTools
        val filteredTools = allTools.filter { it.name in allowedToolNames }
        return filteredTools
    }

    private fun recordAssistantMessage(decisions: List<ToolCallDecision>) {
        val toolCalls =
            decisions.map { decision ->
                ToolCall(
                    id = decision.id,
                    functionCall = FunctionCall(decision.tool, JsonObject(decision.arguments)),
                )
            }
        conversation.add(AssistantMessage(text = null, toolCallList = ToolCallList(toolCalls)))
    }

    private suspend fun calibrateIfNeeded(
        mcp: McpClient,
        sessionId: String,
        snapshot: GameSnapshot,
        toolLog: MutableList<String>,
    ) {
        val yaw = snapshot.agent?.pose?.yaw ?: snapshot.player.pose.yaw
        if (yaw != 0.0F) {
            log.debug("Взгляд надо откалибровать")
            calibrate(mcp, sessionId, -1.0F * yaw)
            val newSnapshot = observe(mcp, sessionId, toolLog)
            log.debug("Новый взгляд: {}", newSnapshot?.agent?.pose?.yaw ?: newSnapshot?.player?.pose?.yaw)
        }
    }

    private fun isTerminalPhase(phase: String): Boolean =
        phase == SessionPhase.LEVEL_COMPLETE.name || phase == SessionPhase.GAME_OVER.name

    private data class StepOutcome(
        val steps: Int,
        val finalPhase: String,
        val finalHp: Int? = null,
    )

    private suspend fun createSession(
        mcp: McpClient,
        seed: Long?,
        log: MutableList<String>,
    ): String? {
        val args =
            buildJsonObject {
                seed?.let { put("seed", it) }
                put("twoLevel", false)
            }.mapValues { it.value }
        val result = mcp.callTool("game_new_session", args)
        log.add("game_new_session -> error=${result.isError}")
        if (result.isError) return null
        return json.decodeFromString<GameSnapshot>(result.text).sessionId
    }

    private suspend fun observe(
        mcp: McpClient,
        sessionId: String,
        log: MutableList<String>,
    ): GameSnapshot? {
        val args =
            mapOf(
                "sessionId" to json.parseToJsonElement("\"$sessionId\""),
            )
        val result = mcp.callTool("game_observe", args)
        log.add("game_observe -> error=${result.isError}")
        if (result.isError) return null
        return json.decodeFromString<GameSnapshot>(result.text)
    }

    private fun failed(
        message: String,
        log: List<String>,
    ) = AgentRunResponse(
        status = "FAILED",
        message = message,
        toolCallLog = log,
    )

    private suspend fun calibrate(
        mcp: McpClient,
        sessionId: String,
        yaw: Float,
    ) {
        val args =
            mapOf(
                "sessionId" to json.parseToJsonElement("\"$sessionId\""),
                "yawDelta" to JsonPrimitive(yaw),
                "deltaMS" to JsonPrimitive(50),
            )
        val result = mcp.callTool("game_sync", args)
        log.debug("game_sync -> error=${result.isError}")
    }
}

private fun estimateConversationTokens(messages: List<LLMMessage>): Int {
    var chars = 0
    for (msg in messages) {
        chars += msg.text?.length ?: 0
        msg.toolCallList?.toolCalls?.forEach { call ->
            chars += call.functionCall.name.length
            chars += call.functionCall.arguments.toString().length
        }
        msg.toolResultList?.toolResults?.forEach { result ->
            chars += result.functionResult.content.length
        }
    }
    return chars / 4
}
