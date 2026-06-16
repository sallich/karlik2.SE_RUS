package ru.course.roguelike.agent

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val llmProvider: String,
    val mcpTransport: String,
)

@Serializable
data class AgentStatusResponse(
    val mode: String,
    val message: String,
    val budgetRemaining: Int,
)

@Serializable
data class MobDecideRequest(
    val mobId: Long,
    val mobX: Float,
    val mobY: Float,
    val playerX: Float,
    val playerY: Float,
    val distance: Float,
    val playerHp: Int,
)

@Serializable
data class MobDecideResponse(
    val intent: String,
    val source: String = "heuristic",
)
