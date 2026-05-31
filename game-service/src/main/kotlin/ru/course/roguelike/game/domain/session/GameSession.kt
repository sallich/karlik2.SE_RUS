package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.combat.MobEntity
import ru.course.roguelike.game.domain.combat.ProjectileEntity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.dto.BossRoomSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.GridPos
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
    var playerHp: Int = ExperienceProgression.maxHpForLevel(ExperienceProgression.STARTING_LEVEL),
    var playerMaxHp: Int = ExperienceProgression.maxHpForLevel(ExperienceProgression.STARTING_LEVEL),
    var playerLevel: Int = ExperienceProgression.STARTING_LEVEL,
    var playerExperience: Int = 0,
    var playerAttackDamage: Int = ExperienceProgression.attackDamageForLevel(ExperienceProgression.STARTING_LEVEL),
    var locationCompletionAwarded: Boolean = false,
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
    val mobs: MutableList<MobEntity> = mutableListOf(),
    val projectiles: MutableList<ProjectileEntity> = mutableListOf(),
    var nextEntityId: Long = 1,
    var playerAttackCooldownMs: Int = 0,
    val keyPickups: MutableList<KeyPickup> = mutableListOf(),
    val bossRoom: Room? = null,
    val exitGate: GridPos? = null,
    var levelCompleted: Boolean = false,
    /** Кооп-агент (видимый спутник); null — сессия без агента. */
    var agentPose: PlayerPose? = null,
) {
    val keysCollected: Int get() = keyPickups.count { it.collected }
    val keysRequired: Int get() = keyPickups.size

    /** Карта активного уровня — её видят системы движения, урона и снимок. */
    val activeMap: TileMap get() = if (currentLevel == 1) secondLevel ?: map else map

    fun allocateEntityId(): Long = nextEntityId++

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
        player = playerSnapshot(),
        agent = agentPose?.let { pose -> playerSnapshot().copy(pose = pose) },
        tick = tick,
        serverTimeMs = serverTimeMs,
        currentLevel = currentLevel,
        mobs = mobs.filter { it.alive }.map { it.toSnapshot() },
        projectiles = projectiles.map { it.toSnapshot() },
        keysCollected = keysCollected,
        keysRequired = keysRequired,
        keyPickups = keyPickups.filter { !it.collected }.map { it.toSnapshot() },
        bossRoom = bossRoom?.toSnapshot(),
        exitGate = exitGate,
    )

    private fun playerSnapshot(): PlayerSnapshot {
        val (xpInLevel, xpToNext) = ExperienceProgression.xpProgressInLevel(playerExperience)
        return PlayerSnapshot(
            pose = playerPose,
            hp = playerHp,
            maxHp = playerMaxHp,
            level = playerLevel,
            experience = xpInLevel,
            experienceToNextLevel = xpToNext,
            attackDamage = playerAttackDamage,
        )
    }
}

private fun Room.toSnapshot() = BossRoomSnapshot(
    x = x,
    y = y,
    width = width,
    height = height,
)
