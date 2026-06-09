package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.combat.MobEntity
import ru.course.roguelike.game.domain.combat.ProjectileEntity
import ru.course.roguelike.game.domain.inventory.InventoryGrid
import ru.course.roguelike.game.domain.inventory.InventorySnapshots
import ru.course.roguelike.game.domain.inventory.InventoryWeapons
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.dto.BossRoomSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InventoryConstants
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
    /** Боезапас в магазине экипированного оружия. */
    var playerAmmo: Int = InventoryConstants.STARTING_LOADED_AMMO,
    val inventory: InventoryGrid = InventoryGrid(
        columns = InventoryConstants.GRID_COLUMNS,
        rows = InventoryConstants.GRID_ROWS,
    ),
    /** Слоты hotbar (id предметов в инвентаре). */
    val hotbarSlots: Array<Int?> = arrayOfNulls(InventoryConstants.HOTBAR_SLOTS),
    var selectedHotbarSlot: Int = 0,
    var equippedWeaponItemId: Int? = null,
    /** Выбранная ячейка инвентаря (Tab+Q/F). */
    var selectedInventoryItemId: Int? = null,
    /** Заряженные патроны для каждого оружия (по id предмета в инвентаре). */
    val weaponLoadedAmmo: MutableMap<Int, Int> = mutableMapOf(),
    var elevatorPhase: ElevatorPhase = ElevatorPhase.IDLE,
    var playerVerticalVelocity: Float = 0f,
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
    val itemPickups: MutableList<ItemPickup> = mutableListOf(),
    var nextItemId: Int = 0,
    val bossRoom: Room? = null,
    val rooms: List<Room> = emptyList(),
    val roomEngagements: MutableList<RoomEngagementState> = mutableListOf(),
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

    fun allocateItemId(): Int = nextItemId++

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
        elevatorPhase = elevatorPhase.name,
        mobs = mobs.filter { it.alive }.map { it.toSnapshot() },
        projectiles = projectiles.map { it.toSnapshot() },
        keysCollected = keysCollected,
        keysRequired = keysRequired,
        keyPickups = keyPickups
            .filter { !it.collected && RoomVisibility.isKeyVisible(this, it) }
            .map { it.toSnapshot() },
        items = itemPickups
            .filter { !it.collected && RoomVisibility.isItemVisible(this, it) }
            .map { it.toSnapshot() },
        bossRoom = bossRoom?.toSnapshot(),
        roomClearTimer = RoomEngagementSystem.timerSnapshot(this),
        exitGate = exitGate,
        doorMarkers = RoomVisibility.doorMarkers(this),
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
            ammo = playerAmmo,
            maxAmmo = InventoryWeapons.magazineCapacity(this),
            inventory = InventorySnapshots.toInventorySnapshot(this),
            hotbar = InventorySnapshots.toHotbarSnapshot(this),
            equippedWeaponName = InventoryWeapons.equippedWeaponName(this),
            equippedWeaponType = InventoryWeapons.equippedWeaponType(this)?.name,
            verticalVelocity = playerVerticalVelocity,
        )
    }
}

private fun Room.toSnapshot() = BossRoomSnapshot(
    x = x,
    y = y,
    width = width,
    height = height,
)
