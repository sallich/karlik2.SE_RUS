package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType

object InventoryWeapons {
    fun equippedWeaponType(session: GameSession): InventoryItemType? =
        session.equippedWeaponItemId?.let { id -> session.inventory.find(id)?.type }

    fun magazineCapacity(session: GameSession): Int {
        val weaponType = equippedWeaponType(session) ?: InventoryItemType.PISTOL
        return InventoryDefinitions.magazineCapacity(weaponType)
    }

    fun reloadEquippedWeapon(session: GameSession): List<GameEvent> = reloadFromInventory(session)

    fun tryAutoReload(session: GameSession): List<GameEvent> {
        if (session.playerAmmo > 0) return emptyList()
        return reloadEquippedWeapon(session)
    }

    fun equippedWeaponName(session: GameSession): String? =
        session.equippedWeaponItemId?.let { id ->
            session.inventory.find(id)?.let { InventoryDefinitions.displayName(it.type) }
        }

    fun assignWeaponToHotbarIfFree(session: GameSession, weaponId: Int) {
        if (session.hotbarSlots.any { it == weaponId }) return
        val freeIndex = session.hotbarSlots.indexOfFirst { it == null }
        if (freeIndex >= 0) session.hotbarSlots[freeIndex] = weaponId
    }

    fun equipWeapon(session: GameSession, itemId: Int): List<GameEvent> {
        val item = session.inventory.find(itemId) ?: return emptyList()
        if (!InventoryDefinitions.isWeapon(item.type)) return emptyList()

        session.equippedWeaponItemId?.let { previousId ->
            session.weaponLoadedAmmo[previousId] = session.playerAmmo
        }
        session.equippedWeaponItemId = itemId
        val slotIndex = session.hotbarSlots.indexOf(itemId)
        if (slotIndex >= 0) session.selectedHotbarSlot = slotIndex
        session.playerAmmo = session.weaponLoadedAmmo[itemId] ?: 0
        session.playerAttackDamage = InventorySystem.recalculateAttackDamage(session)
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
        val needed = (magCapacity - session.playerAmmo).coerceAtLeast(0)
        if (needed <= 0) return emptyList()

        val taken = session.inventory.consumeAmmo(needed, ammoType)
        return if (taken <= 0) {
            emptyList()
        } else {
            session.playerAmmo += taken
            session.equippedWeaponItemId?.let { session.weaponLoadedAmmo[it] = session.playerAmmo }
            listOf(GameEvent.AmmoChanged(taken, session.playerAmmo))
        }
    }
}
