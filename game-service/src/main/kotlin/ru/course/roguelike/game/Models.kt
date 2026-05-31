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
    /** Создать двухуровневую локацию с лифтами (issue #3, опционально). */
    val twoLevel: Boolean = false,
    /** Спавнить кооп-агента рядом с игроком. */
    val coopAgent: Boolean = false,
)

@Serializable
data class PlayerActionRequest(
    val action: String,
    /** "player" (default) или "agent" для кооп-агента. */
    val actor: String = "player",
)
