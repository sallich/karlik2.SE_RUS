package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class KeySnapshot(
    val id: Int,
    val x: Float,
    val y: Float,
)

@Serializable
data class BossRoomSnapshot(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
