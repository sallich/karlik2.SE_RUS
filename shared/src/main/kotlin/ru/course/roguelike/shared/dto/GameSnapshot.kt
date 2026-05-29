package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

@Serializable
data class GameSnapshot(
    val sessionId: String,
    val seed: Long,
    val phase: String,
    val width: Int,
    val height: Int,
    val tiles: List<TileType>,
    val player: PlayerSnapshot,
    val tick: Long,
    /** Серверное время снимка (мс), для интерполяции на клиенте. */
    val serverTimeMs: Long = 0L,
)

@Serializable
data class PlayerSnapshot(
    val pose: PlayerPose,
    val hp: Int,
    val maxHp: Int,
)
