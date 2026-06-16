package ru.course.roguelike.agent.llm

import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot

interface AgentDecisionClient {
    suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        actor: String = KeyHuntPlanner.ACTOR_PLAYER,
    ): ToolCallDecision
}

class HeuristicDecisionClient(
    private val planner: KeyHuntPlanner = KeyHuntPlanner(),
) : AgentDecisionClient {
    override suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        actor: String,
    ): ToolCallDecision = planner.plan(snapshot, sessionId, actor)
}

class LlmClientFactory {
    fun create(config: AgentConfig, fallback: AgentDecisionClient): AgentDecisionClient =
        when (config.llmProvider.lowercase()) {
            "yandex" -> YandexGptClient(config, fallback)
            "heuristic", "stub" -> HeuristicDecisionClient()
            else -> HeuristicDecisionClient()
        }
}
