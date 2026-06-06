package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.model.InventoryDefinitions

object InventoryHotbar {
    fun handleHotbarSelect(session: GameSession, hotbarSelect: Int?): List<GameEvent> {
        if (hotbarSelect == null || hotbarSelect !in session.hotbarSlots.indices) return emptyList()
        session.selectedHotbarSlot = hotbarSelect
        return session.hotbarSlots[hotbarSelect]?.let { InventoryWeapons.equipWeapon(session, it) }.orEmpty()
    }

    fun handleHotbarAssign(session: GameSession, hotbarAssign: Int?): List<GameEvent> {
        if (hotbarAssign == null || hotbarAssign !in session.hotbarSlots.indices) return emptyList()
        return cycleWeaponIntoHotbarSlot(session, hotbarAssign)
    }

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
}
