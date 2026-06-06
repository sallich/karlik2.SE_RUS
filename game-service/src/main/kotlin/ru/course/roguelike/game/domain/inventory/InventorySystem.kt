package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot
import ru.course.roguelike.shared.model.InventoryConstants
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType
import ru.course.roguelike.shared.model.ItemKind

object InventorySystem {
    fun initialize(session: GameSession) {
        StarterLoadout.apply(session)
    }

    fun toInventorySnapshot(grid: InventoryGrid): InventorySnapshot = InventorySnapshot(
        columns = grid.columns,
        rows = grid.rows,
        items = grid.items.map { item ->
            ru.course.roguelike.shared.dto.InventoryItemSnapshot(
                id = item.id,
                type = item.type.name,
                quantity = item.quantity,
                col = item.col,
                row = item.row,
                width = item.width,
                height = item.height,
                damageBonus = item.totalDamageBonus(),
                displayName = InventoryDefinitions.displayName(item.type),
            )
        },
    )

    fun toHotbarSnapshot(session: GameSession): HotbarSnapshot = HotbarSnapshot(
        slots = session.hotbarSlots.toList(),
        equippedItemId = session.equippedWeaponItemId,
        selectedSlot = session.selectedHotbarSlot,
    )

    fun inventoryTypeForMapItem(kind: ItemKind): InventoryItemType = when (kind) {
        ItemKind.AMMO_PISTOL -> InventoryItemType.PISTOL_AMMO
        ItemKind.AMMO_SHOTGUN -> InventoryItemType.SHOTGUN_AMMO
        ItemKind.HEALTH -> InventoryItemType.HEALTH_KIT
        ItemKind.WEAPON_PISTOL -> InventoryItemType.PISTOL
        ItemKind.WEAPON_SHOTGUN -> InventoryItemType.SHOTGUN
        ItemKind.EXPERIENCE -> error("Experience orbs are not stored in inventory")
    }

    fun quantityForMapItem(kind: ItemKind): Int = when (kind) {
        ItemKind.AMMO_PISTOL, ItemKind.AMMO_SHOTGUN -> InventoryConstants.AMMO_STACK_SIZE
        else -> 1
    }

    fun damageBonusForMapItem(kind: ItemKind): Int = when (kind) {
        ItemKind.WEAPON_PISTOL, ItemKind.WEAPON_SHOTGUN -> InventoryConstants.PICKUP_WEAPON_DAMAGE_BONUS
        else -> 0
    }

    fun equippedWeaponType(session: GameSession): InventoryItemType? =
        session.equippedWeaponItemId?.let { id -> session.inventory.find(id)?.type }

    fun collectFromMap(session: GameSession, kind: ItemKind, @Suppress("UNUSED_PARAMETER") worldItemId: Int): List<GameEvent> {
        val type = inventoryTypeForMapItem(kind)
        val result = session.inventory.add(
            type = type,
            quantity = quantityForMapItem(kind),
            damageBonus = damageBonusForMapItem(kind),
        )
        return when (result) {
            is AddItemResult.Added -> {
                val events = mutableListOf<GameEvent>(GameEvent.ItemAddedToInventory(result.item.id, type.name))
                if (InventoryDefinitions.isWeapon(type)) {
                    assignWeaponToHotbarIfFree(session, result.item.id)
                    events.addAll(equipWeapon(session, result.item.id))
                } else if (type == InventoryItemType.HEALTH_KIT) {
                    events.addAll(useHealthKitIfNeeded(session, result.item))
                }
                events
            }
            is AddItemResult.Stacked -> listOf(GameEvent.ItemStackedInInventory(type.name))
            is AddItemResult.InventoryFull -> listOf(GameEvent.InventoryFull(type.name))
        }
    }

    fun handleHotbarInput(
        session: GameSession,
        hotbarSelect: Int?,
        hotbarAssign: Int?,
        reload: Boolean,
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        if (hotbarAssign != null && hotbarAssign in session.hotbarSlots.indices) {
            events.addAll(cycleWeaponIntoHotbarSlot(session, hotbarAssign))
        }
        if (hotbarSelect != null && hotbarSelect in session.hotbarSlots.indices) {
            session.selectedHotbarSlot = hotbarSelect
            session.hotbarSlots[hotbarSelect]?.let { weaponId ->
                events.addAll(equipWeapon(session, weaponId))
            }
        }
        if (reload) {
            events.addAll(reloadEquippedWeapon(session))
        }
        return events
    }

    fun magazineCapacity(session: GameSession): Int {
        val weaponType = equippedWeaponType(session) ?: InventoryItemType.PISTOL
        return InventoryDefinitions.magazineCapacity(weaponType)
    }

    fun reloadEquippedWeapon(session: GameSession): List<GameEvent> = reloadFromInventory(session)

    fun tryAutoReload(session: GameSession): List<GameEvent> {
        if (session.playerAmmo > 0) return emptyList()
        return reloadEquippedWeapon(session)
    }

    fun recalculateAttackDamage(session: GameSession): Int {
        val base = ru.course.roguelike.shared.model.ExperienceProgression
            .attackDamageForLevel(session.playerLevel)
        val weaponBonus = session.equippedWeaponItemId?.let { id ->
            session.inventory.find(id)?.totalDamageBonus()
        } ?: 0
        return base + weaponBonus
    }

    fun equippedWeaponName(session: GameSession): String? =
        session.equippedWeaponItemId?.let { id ->
            session.inventory.find(id)?.let { InventoryDefinitions.displayName(it.type) }
        }

    /** Tab+1/2: по кругу назначает оружие из сетки в слот hotbar. */
    private fun cycleWeaponIntoHotbarSlot(session: GameSession, slot: Int): List<GameEvent> {
        val weapons = session.inventory.items
            .filter { InventoryDefinitions.isWeapon(it.type) }
            .sortedBy { it.id }
        if (weapons.isEmpty()) return emptyList()

        val otherSlot = 1 - slot
        val blockedId = session.hotbarSlots[otherSlot]
        val candidates = weapons.filter { it.id != blockedId }
        if (candidates.isEmpty()) return emptyList()

        val currentId = session.hotbarSlots[slot]
        val nextIndex = if (currentId == null) {
            0
        } else {
            val idx = candidates.indexOfFirst { it.id == currentId }
            if (idx < 0) 0 else (idx + 1) % candidates.size
        }
        val nextWeapon = candidates[nextIndex]
        session.hotbarSlots[slot] = nextWeapon.id
        return listOf(GameEvent.HotbarWeaponAssigned(slot, nextWeapon.id, nextWeapon.type.name))
    }

    private fun assignWeaponToHotbarIfFree(session: GameSession, weaponId: Int) {
        if (session.hotbarSlots.any { it == weaponId }) return
        val freeIndex = session.hotbarSlots.indexOfFirst { it == null }
        if (freeIndex >= 0) session.hotbarSlots[freeIndex] = weaponId
    }

    private fun equipWeapon(session: GameSession, itemId: Int): List<GameEvent> {
        val item = session.inventory.find(itemId) ?: return emptyList()
        if (!InventoryDefinitions.isWeapon(item.type)) return emptyList()

        session.equippedWeaponItemId?.let { previousId ->
            session.weaponLoadedAmmo[previousId] = session.playerAmmo
        }
        session.equippedWeaponItemId = itemId
        session.selectedHotbarSlot = session.hotbarSlots.indexOf(itemId).takeIf { it >= 0 } ?: session.selectedHotbarSlot
        session.playerAmmo = session.weaponLoadedAmmo[itemId] ?: 0
        session.playerAttackDamage = recalculateAttackDamage(session)
        return listOf(
            GameEvent.WeaponEquipped(
                itemId = itemId,
                weaponName = InventoryDefinitions.displayName(item.type),
                attackDamage = session.playerAttackDamage,
            ),
        )
    }

    private fun reloadFromInventory(session: GameSession): List<GameEvent> {
        val weaponType = equippedWeaponType(session) ?: return emptyList()
        val ammoType = InventoryDefinitions.ammoTypeForWeapon(weaponType) ?: return emptyList()
        val magCapacity = InventoryDefinitions.magazineCapacity(weaponType)
        if (session.playerAmmo >= magCapacity) return emptyList()

        val needed = magCapacity - session.playerAmmo
        val taken = session.inventory.consumeAmmo(needed, ammoType)
        if (taken <= 0) return emptyList()

        session.playerAmmo += taken
        session.equippedWeaponItemId?.let { session.weaponLoadedAmmo[it] = session.playerAmmo }
        return listOf(GameEvent.AmmoChanged(taken, session.playerAmmo))
    }

    /** Аптечка применяется сразу при подборе, если HP не полное. */
    private fun useHealthKitIfNeeded(session: GameSession, item: InventoryItem): List<GameEvent> {
        if (session.playerHp >= session.playerMaxHp) return emptyList()
        return useHealthKit(session, item)
    }

    private fun useHealthKit(session: GameSession, item: InventoryItem): List<GameEvent> {
        val before = session.playerHp
        session.playerHp = (before + InventoryConstants.HEALTH_KIT_RESTORE).coerceAtMost(session.playerMaxHp)
        val healed = session.playerHp - before
        if (healed <= 0) return emptyList()
        session.inventory.remove(item.id)
        return listOf(GameEvent.PlayerHealed(healed, session.playerHp))
    }
}
