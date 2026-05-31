package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionPhase {
    EXPLORATION,
    COMBAT,
    CHOICE,
    HUB,
    GAME_OVER,
    LEVEL_COMPLETE,
}
