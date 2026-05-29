package ru.course.roguelike.game

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
)

@Serializable
data class CreateSessionRequest(
    val seed: Long? = null,
)

@Serializable
data class PlayerActionRequest(
    val action: String,
)
