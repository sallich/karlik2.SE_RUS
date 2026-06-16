package ru.course.roguelike.agent.llm

import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.config.AgentConfig

class MobDecisionService(
    private val config: AgentConfig,
) {
    fun decide(request: MobDecideRequest): MobDecideResponse {
        val intent = when {
            request.distance <= 2.5f && request.playerHp > 0 -> "shoot"
            request.distance < 1.2f -> "kite"
            request.distance > 4f -> "chase"
            else -> "idle"
        }
        val source = if (config.llmProvider == "yandex") "yandex-fallback" else "heuristic"
        return MobDecideResponse(intent = intent, source = source)
    }
}
