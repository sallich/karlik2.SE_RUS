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
    /** Накопитель дробного урона лавой (HP списывается целыми единицами). */
    var lavaDamageBuffer: Float = 0f,
    var tick: Long = 0,
    var serverTimeMs: Long = System.currentTimeMillis(),
    /** Верхний уровень двухуровневой локации (null — одноуровневая сессия). */
    val secondLevel: TileMap? = null,
    /** Активный уровень (0 — нижний [map], 1 — [secondLevel]). */
    var currentLevel: Int = 0,
    /** Стоял ли герой на лифте в прошлом тике — чтобы не зациклить переход. */
    var onElevator: Boolean = false,
) {
    /** Карта активного уровня — её видят системы движения, урона и снимок. */
    val activeMap: TileMap get() = if (currentLevel == 1) secondLevel ?: map else map

    fun touchClock() {
        tick++
        serverTimeMs = System.currentTimeMillis()
    }

    fun toSnapshot(): GameSnapshot = GameSnapshot(
        sessionId = sessionId,
        seed = seed,
        phase = phase.name,
        width = activeMap.width,
        height = activeMap.height,
        tiles = activeMap.toFlatList(),
        player = PlayerSnapshot(
            pose = playerPose,
            hp = playerHp,
            maxHp = playerMaxHp,
        ),
        tick = tick,
        serverTimeMs = serverTimeMs,
        currentLevel = currentLevel,
    )
}
