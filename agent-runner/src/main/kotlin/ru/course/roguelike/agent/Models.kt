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
data class AgentRunRequest(
    val seed: Long? = null,
    val maxSteps: Int = 10,
)

@Serializable
data class AgentRunResponse(
    val status: String,
    val message: String,
    val stepsPlanned: Int,
)
