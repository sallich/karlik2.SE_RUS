package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class TileType {
    FLOOR,
    WALL,
    ;

    val walkable: Boolean get() = this == FLOOR
    val blocksVision: Boolean get() = this == WALL
}
