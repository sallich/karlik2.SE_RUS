package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.HotbarSnapshot
import ru.course.roguelike.shared.dto.InventorySnapshot
import ru.course.roguelike.shared.model.InventoryDefinitions

object InventorySnapshots {
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
}
