package ru.course.roguelike.agent.loop

import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.AgentRunResponse
import ru.course.roguelike.agent.config.AgentConfig

/**
 * Placeholder for the real agent loop: LLM chat + MCP tool calls only.
 * Must not import or depend on game-service internals.
 */
class StubAgentLoop {
    fun planRun(request: AgentRunRequest, config: AgentConfig): AgentRunResponse {
        val steps = minOf(request.maxSteps, config.maxToolCalls)
        return AgentRunResponse(
            status = "STUB",
            message = "Planned $steps tool-call steps via MCP (${config.mcpTransport}). " +
                "LLM provider: ${config.llmProvider}. Implement loop in next milestones.",
            stepsPlanned = steps,
        )
    }
}
