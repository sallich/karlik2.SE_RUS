package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayerActionResponse(
    val accepted: Boolean,
    val message: String,
    val snapshot: GameSnapshot? = null,
)
