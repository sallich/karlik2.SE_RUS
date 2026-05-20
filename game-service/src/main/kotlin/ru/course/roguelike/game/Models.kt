package ru.course.roguelike.game

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
)

@Serializable
data class GameSessionResponse(
    val sessionId: String,
    val seed: Long,
    val phase: String,
    val message: String,
)

@Serializable
data class CreateSessionRequest(
    val seed: Long? = null,
)

@Serializable
data class PlayerActionRequest(
    val action: String,
)

@Serializable
data class PlayerActionResponse(
    val accepted: Boolean,
    val message: String,
)
