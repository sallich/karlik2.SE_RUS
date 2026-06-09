package ru.course.roguelike.client

import com.badlogic.gdx.Gdx
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.SessionPhase

internal fun RoguelikeGame.buildSyncBindings(): SyncBindings = SyncBindings(
    poseAccessor = { predictedPose },
    poseMutator = { predictedPose = it },
    authoritativeMutator = { authoritativePose = it },
    vitalsMutator = { hp, maxHp, level, experience, xpToNext, ammo, maxAmmo, weaponName, weaponType ->
        playerHp = hp
        playerMaxHp = maxHp
        playerLevel = level
        playerExperience = experience
        playerExperienceToNextLevel = xpToNext
        playerAmmo = ammo
        playerMaxAmmo = maxAmmo
        equippedWeaponName = weaponName
        equippedWeaponType = weaponType
    },
    inventoryMutator = { inventory, hotbar ->
        playerInventory = inventory
        playerHotbar = hotbar
    },
    combatMutator = { mobs, projectiles ->
        serverMobs = mobs
        serverProjectiles = projectiles
    },
    progressMutator = { phase, collected, required, keys, locationItems, gate ->
        sessionPhase = parseSessionPhase(phase)
        keysCollected = collected
        keysRequired = required
        keyPickups = keys
        items = locationItems
        exitGate = gate
    },
    agentMutator = { agentPose = it },
    verticalMutator = { clientVerticalVelocity = it },
    elevatorPhaseMutator = { phase ->
        clientElevatorPhase = runCatching { ElevatorPhase.valueOf(phase) }
            .getOrDefault(ElevatorPhase.IDLE)
    },
    roomTimerMutator = { timer, serverTimeMs ->
        roomClearTimer = timer
        roomClearTimerReceivedAtMs = serverTimeMs
    },
)

internal fun RoguelikeGame.resetSessionState() {
    predictedPose = null
    authoritativePose = null
    tileMap = null
    visitedTracker.clear()
    currentLevel = 0
    keyPickups = emptyList()
    items = emptyList()
    exitGate = null
    roomClearTimer = null
    roomClearTimerReceivedAtMs = 0L
    sessionPhase = SessionPhase.EXPLORATION
    playerHp = 0
    playerMaxHp = 0
    playerLevel = 1
    playerExperience = 0
    playerExperienceToNextLevel = 100
    playerAmmo = 0
    playerMaxAmmo = 0
    equippedWeaponName = null
    equippedWeaponType = null
    playerInventory = null
    playerHotbar = null
    showInventoryGrid = false
    clientVerticalVelocity = 0f
    clientElevatorPhase = ElevatorPhase.IDLE
    clientWasOnElevator = false
    keysCollected = 0
    keysRequired = 0
    serverMobs = emptyList()
    serverProjectiles = emptyList()
    agentPose = null
    pendingSyncInput = InputSyncRequest()
    pendingSyncDeltaMs = 0
    accumulatedYawDelta = 0f
    syncAccum = 0f
    lastCollisionDebug = null
    statusLine = "Starting new run..."
}

internal fun RoguelikeGame.applyServerSnapshot(snap: GameSnapshot) {
    if (snap.currentLevel != currentLevel) {
        currentLevel = snap.currentLevel
        visitedTracker.clear()
    }
    clientVerticalVelocity = snap.player.verticalVelocity
    clientElevatorPhase = runCatching { ElevatorPhase.valueOf(snap.elevatorPhase) }
        .getOrDefault(ElevatorPhase.IDLE)
    tileMap = TileMap.fromFlat(snap.width, snap.height, snap.tiles)
    serverMobs = snap.mobs
    serverProjectiles = snap.projectiles
    sessionPhase = parseSessionPhase(snap.phase)
    keysCollected = snap.keysCollected
    keysRequired = snap.keysRequired
    keyPickups = snap.keyPickups
    items = snap.items
    exitGate = snap.exitGate
    roomClearTimer = snap.roomClearTimer
    roomClearTimerReceivedAtMs = snap.serverTimeMs
    audio.onCombatSnapshot(snap.player.hp, snap.projectiles)
    if (snap.player.pose.isGrounded && clientElevatorPhase == ElevatorPhase.IDLE) {
        clientVerticalVelocity = 0f
    }
    sync.applySnapshot(snap)
}

internal fun RoguelikeGame.drawGameHud() {
    val pose = predictedPose
    val onLava = !isSessionEnded && pose != null && tileMap?.getTileAt(pose.x, pose.y)?.damaging == true
    hud.draw(
        statusLine,
        pose,
        lastCollisionDebug,
        showCollisionDebug && !isSessionEnded,
        onLava = onLava,
        hp = playerHp,
        maxHp = playerMaxHp,
        level = playerLevel,
        experience = playerExperience,
        experienceToNextLevel = playerExperienceToNextLevel,
        ammo = playerAmmo,
        maxAmmo = playerMaxAmmo,
        equippedWeaponName = equippedWeaponName,
        equippedWeaponType = equippedWeaponType,
        hotbar = playerHotbar,
        inventory = playerInventory,
        inventoryOpen = showInventoryGrid,
        floorLevel = currentLevel,
        floorCount = if (twoLevelLocation) 2 else 1,
        keysCollected = keysCollected,
        keysRequired = keysRequired,
        interactionHint = interactionHint(
            pose,
            isSessionEnded,
            tileMap,
            exitGate,
            KeyProgress(keysCollected, keysRequired),
            keyPickups,
            items,
        ),
    )
}

internal fun RoguelikeGame.drawInventoryPanel() {
    if (isSessionEnded) return
    inventoryUiOverlay.render(
        screenW = Gdx.graphics.width.toFloat(),
        screenH = Gdx.graphics.height.toFloat(),
        inventory = playerInventory,
        hotbar = playerHotbar,
        equippedWeaponType = equippedWeaponType,
        expanded = showInventoryGrid,
    )
}
