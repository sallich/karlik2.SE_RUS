package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class MobKind {
    MELEE,
    RANGED,
}
