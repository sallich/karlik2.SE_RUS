package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.MobKind

@Serializable
data class MobSnapshot(
    val id: Long,
    val kind: MobKind,
    val x: Float,
    val y: Float,
    val hp: Int,
    val maxHp: Int,
)

@Serializable
data class ProjectileSnapshot(
    val id: Long,
    val x: Float,
    val y: Float,
    val fromPlayer: Boolean = false,
)
