package ru.course.roguelike.game.domain.session

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase

/**
 * Мутабельное состояние одной игровой сессии (источник правды на сервере).
 */
data class GameSession(
    val sessionId: String,
    val seed: Long,
    var phase: SessionPhase = SessionPhase.EXPLORATION,
    val map: TileMap,
    var playerPose: PlayerPose,
    var playerHp: Int = 100,
    val playerMaxHp: Int = 100,
    var tick: Long = 0,
    var serverTimeMs: Long = System.currentTimeMillis(),
) {
    fun touchClock() {
        tick++
        serverTimeMs = System.currentTimeMillis()
    }

    fun toSnapshot(): GameSnapshot = GameSnapshot(
        sessionId = sessionId,
        seed = seed,
        phase = phase.name,
        width = map.width,
        height = map.height,
        tiles = map.toFlatList(),
        player = PlayerSnapshot(
            pose = playerPose,
            hp = playerHp,
            maxHp = playerMaxHp,
        ),
        tick = tick,
        serverTimeMs = serverTimeMs,
    )
}
