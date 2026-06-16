package ru.course.roguelike.policy

import kotlinx.serialization.Serializable
import ru.course.roguelike.agent.tracker.LlmHealthResponse
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.loop.MacroDecision

@Serializable
data class PolicyHealthResponse(
    val status: String,
    val service: String,
    val agentType: String = "policy-dsl",
    val llmProvider: String,
    val mcpTransport: String,
    val ollamaBaseUrl: String? = null,
    val ollamaModel: String? = null,
    val ollamaFallbackModel: String? = null,
    val requireLlm: Boolean = true,
    val llm: LlmHealthResponse? = null,
)

@Serializable
data class PolicyAgentStatusResponse(
    val mode: String,
    val message: String,
    val budgetRemaining: Int,
    val agentType: String = "policy-dsl",
)

@Serializable
data class PolicyRunRequest(
    val seed: Long? = null,
    val maxSteps: Int = 20_000,
    val sessionId: String? = null,
)

@Serializable
data class PolicyRunResponse(
    val status: String,
    val message: String,
    val stepsUsed: Int = 0,
    val stepsPlanned: Int = 0,
    val sessionId: String? = null,
    val finalPhase: String? = null,
    val success: Boolean = false,
    val toolCallLog: List<String> = emptyList(),
    val replanCount: Int = 0,
    val replanLog: List<String> = emptyList(),
    /** LLM macro decisions (initial + each replan), not per-step. */
    val macroDecisions: List<MacroDecision> = emptyList(),
    val finalPolicy: AgentPolicy? = null,
    val policyTokensApprox: Int = 0,
    val stuckEvents: Int = 0,
    val knowledgeCellsKnown: Int = 0,
)
