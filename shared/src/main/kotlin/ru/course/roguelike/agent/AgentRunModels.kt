package ru.course.roguelike.agent

import kotlinx.serialization.Serializable

@Serializable
data class AgentRunRequest(
    val seed: Long? = null,
    val maxSteps: Int = 500,
    val sessionId: String? = null,
)

@Serializable
data class AgentRunResponse(
    val status: String,
    val message: String,
    val stepsUsed: Int = 0,
    val stepsPlanned: Int = 0,
    val sessionId: String? = null,
    val finalPhase: String? = null,
    val success: Boolean = false,
    val toolCallLog: List<String> = emptyList(),
)
