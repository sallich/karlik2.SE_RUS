package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.GridPos
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
    /** Кооп-агент в той же сессии (null если отключён). */
    val agent: PlayerSnapshot? = null,
    val tick: Long,
    /** Серверное время снимка (мс), для интерполяции на клиенте. */
    val serverTimeMs: Long = 0L,
    /** Активный уровень локации (0 — нижний). Меняется при использовании лифта. */
    val currentLevel: Int = 0,
    val mobs: List<MobSnapshot> = emptyList(),
    val projectiles: List<ProjectileSnapshot> = emptyList(),
    val keysCollected: Int = 0,
    val keysRequired: Int = 0,
    val keyPickups: List<KeySnapshot> = emptyList(),
    val bossRoom: BossRoomSnapshot? = null,
    /** Ячейка ворот выхода в комнате босса (куда нужно принести ключи). */
    val exitGate: GridPos? = null,
)

@Serializable
data class PlayerSnapshot(
    val pose: PlayerPose,
    val hp: Int,
    val maxHp: Int,
)
