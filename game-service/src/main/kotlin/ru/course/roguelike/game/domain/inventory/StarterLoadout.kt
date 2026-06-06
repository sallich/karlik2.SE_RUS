package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.model.InventoryConstants
import ru.course.roguelike.shared.model.InventoryItemType

object StarterLoadout {
    fun apply(session: GameSession) {
        val pistolResult = session.inventory.add(InventoryItemType.PISTOL)
        val pistolId = when (pistolResult) {
            is AddItemResult.Added -> pistolResult.item.id
            else -> return
        }
        session.inventory.add(InventoryItemType.PISTOL_AMMO, InventoryConstants.AMMO_STACK_SIZE)
        session.hotbarSlots[0] = pistolId
        session.hotbarSlots[1] = null
        session.selectedHotbarSlot = 0
        session.equippedWeaponItemId = pistolId
        session.playerAmmo = InventoryConstants.STARTING_LOADED_AMMO
        session.weaponLoadedAmmo[pistolId] = session.playerAmmo
        session.playerAttackDamage = InventorySystem.recalculateAttackDamage(session)
    }
}
