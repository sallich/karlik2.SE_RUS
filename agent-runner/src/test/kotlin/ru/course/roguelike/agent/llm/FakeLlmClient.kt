package ru.course.roguelike.agent.llm

import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import java.util.concurrent.atomic.AtomicInteger

class FakeLlmClient(
    private val fallback: AgentDecisionClient = HeuristicDecisionClient(),
    private val responses: List<ToolCallDecision?> = listOf(null),
    var lastMessages: List<LLMMessage>? = null,
    var lastTools: List<McpTool>? = null,
    var lastRequest: MobDecideRequest? = null,
    private val response: MobDecideResponse = MobDecideResponse("idle", "fake"),
    private val loop: Boolean = false,
) : AgentDecisionClient {
    private val counter = AtomicInteger(0)

    override suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        actor: String,
    ): List<ToolCallDecision> {
        val idx = counter.getAndIncrement()
        val response =
            if (loop && responses.isNotEmpty()) {
                responses[idx % responses.size]
            } else if (idx < responses.size) {
                responses[idx]
            } else {
                null
            }
        if (response != null) {
            return listOf(response)
        }
        return fallback.chooseTool(snapshot, sessionId, messages, availableTools, actor)
    }

    override suspend fun decide(
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        request: MobDecideRequest
    ): MobDecideResponse {
        lastMessages = messages
        lastTools = availableTools
        lastRequest = request
        return response
    }
}
