package ru.course.roguelike.agent.llm

import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool

interface AgentDecisionClient {
    suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        actor: String = KeyHuntPlanner.ACTOR_PLAYER,
    ): List<ToolCallDecision>

    suspend fun decide(
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        request: MobDecideRequest,
    ): MobDecideResponse
}

class HeuristicDecisionClient(
    private val planner: KeyHuntPlanner = KeyHuntPlanner(),
) : AgentDecisionClient {
    override suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        actor: String,
    ): List<ToolCallDecision> = planner.plan(snapshot, sessionId, actor)

    override suspend fun decide(
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        request: MobDecideRequest,
    ): MobDecideResponse {
        val tool =
            when {
                request.distance <= 2.5f && request.distance > 1.2f && request.playerHp > 0 -> "shoot"
                request.distance <= 1.2f -> "kite"
                request.distance > 4f -> "chase"
                else -> "idle"
            }
        return MobDecideResponse(tool, "heuristic")
    }
}

open class LlmClientFactory {
    private val log = LoggerFactory.getLogger(LlmClientFactory::class.java)

    open fun create(
        config: AgentConfig,
        fallback: AgentDecisionClient,
    ): AgentDecisionClient =
        when (config.llmProvider.lowercase()) {
            "yandex" -> {
                if (hasYandexCredentials(config)) {
                    YandexGptClient(config, fallback)
                } else {
                    log.warn("YandexGPT credentials missing, falling back to heuristic")
                    HeuristicDecisionClient()
                }
            }

            "yandex-openai" -> {
                if (hasYandexCredentials(config)) {
                    YandexOpenAIClient(config, fallback)
                } else {
                    log.warn("YandexGPT (OpenAI compat) credentials missing, falling back to heuristic")
                    HeuristicDecisionClient()
                }
            }

            "ollama" -> {
                if (config.ollamaModelUrl.isBlank()) {
                    log.warn("Ollama URL not set, falling back to heuristic")
                    HeuristicDecisionClient()
                } else {
                    OpenAIClient(config, fallback)
                }
            }

            "heuristic", "stub" -> HeuristicDecisionClient()
            else -> {
                log.warn("Unknown LLM provider '${config.llmProvider}', using heuristic")
                HeuristicDecisionClient()
            }
        }

    private fun hasYandexCredentials(config: AgentConfig): Boolean =
        !config.llmApiKey.isNullOrBlank() && !config.yandexFolderId.isNullOrBlank()
}
