package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.ItemPickup
import ru.course.roguelike.shared.model.InventoryConstants
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType
import ru.course.roguelike.shared.model.ItemKind
import kotlin.math.cos
import kotlin.math.sin

@Suppress("TooManyFunctions")
object InventorySystem {
    fun initialize(session: GameSession) {
        StarterLoadout.apply(session)
    }

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

    fun collectFromMap(
        session: GameSession,
        kind: ItemKind,
        @Suppress("UNUSED_PARAMETER") worldItemId: Int,
    ): List<GameEvent> {
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
                    InventoryWeapons.assignWeaponToHotbarIfFree(session, result.item.id)
                    events.addAll(InventoryWeapons.equipWeapon(session, result.item.id))
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
        events.addAll(InventoryHotbar.handleHotbarAssign(session, hotbarAssign))
        events.addAll(InventoryHotbar.handleHotbarSelect(session, hotbarSelect))
        if (reload) {
            events.addAll(InventoryWeapons.reloadEquippedWeapon(session))
        }
        return events
    }

    fun handleInventoryInput(
        session: GameSession,
        cycle: Boolean,
        drop: Boolean,
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        if (cycle) cycleInventorySelection(session)
        if (drop) dropSelectedItem(session)?.let { events.add(it) }
        return events
    }

    fun mapItemKindForInventoryType(type: InventoryItemType): ItemKind? = when (type) {
        InventoryItemType.PISTOL -> ItemKind.WEAPON_PISTOL
        InventoryItemType.SHOTGUN -> ItemKind.WEAPON_SHOTGUN
        InventoryItemType.PISTOL_AMMO -> ItemKind.AMMO_PISTOL
        InventoryItemType.SHOTGUN_AMMO -> ItemKind.AMMO_SHOTGUN
        InventoryItemType.HEALTH_KIT -> ItemKind.HEALTH
    }

    private fun cycleInventorySelection(session: GameSession) {
        val items = session.inventory.items.sortedBy { it.id }
        if (items.isEmpty()) {
            session.selectedInventoryItemId = null
            return
        }
        val currentIndex = items.indexOfFirst { it.id == session.selectedInventoryItemId }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % items.size
        session.selectedInventoryItemId = items[nextIndex].id
    }

    private fun dropSelectedItem(session: GameSession): GameEvent? {
        val itemId = session.selectedInventoryItemId ?: return null
        val item = session.inventory.find(itemId) ?: run {
            session.selectedInventoryItemId = null
            return null
        }
        val kind = mapItemKindForInventoryType(item.type) ?: return null

        if (session.equippedWeaponItemId == itemId) {
            session.equippedWeaponItemId = null
            session.playerAmmo = 0
            session.playerAttackDamage = recalculateAttackDamage(session)
        }
        for (index in session.hotbarSlots.indices) {
            if (session.hotbarSlots[index] == itemId) session.hotbarSlots[index] = null
        }
        session.weaponLoadedAmmo.remove(itemId)
        session.inventory.remove(itemId)

        val remaining = session.inventory.items.sortedBy { it.id }
        session.selectedInventoryItemId = remaining.firstOrNull()?.id

        val (dropX, dropY) = dropPositionInFront(session)
        val pickup = ItemPickup(
            id = session.allocateItemId(),
            kind = kind,
            x = dropX,
            y = dropY,
        )
        session.itemPickups.add(pickup)
        return GameEvent.ItemDropped(pickup.id, kind, pickup.x, pickup.y)
    }

    private fun dropPositionInFront(session: GameSession): Pair<Float, Float> {
        val pose = session.playerPose
        val distance = 0.85f
        return pose.x + cos(pose.yaw) * distance to pose.y + sin(pose.yaw) * distance
    }

    fun recalculateAttackDamage(session: GameSession): Int {
        val base = ru.course.roguelike.shared.model.ExperienceProgression
            .attackDamageForLevel(session.playerLevel)
        val weaponBonus = session.equippedWeaponItemId?.let { id ->
            session.inventory.find(id)?.totalDamageBonus()
        } ?: 0
        return base + weaponBonus
    }

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
