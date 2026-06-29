package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.model.InventoryConstants
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType
import ru.course.roguelike.shared.model.ItemKind

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
