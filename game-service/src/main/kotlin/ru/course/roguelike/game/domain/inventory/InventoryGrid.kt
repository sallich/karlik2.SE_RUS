package ru.course.roguelike.game.domain.inventory

import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType

/** Предмет в сетке инвентаря игрока. */
data class InventoryItem(
    val id: Int,
    val type: InventoryItemType,
    var quantity: Int = 1,
    val col: Int,
    val row: Int,
    val damageBonus: Int = 0,
) {
    val width: Int get() = InventoryDefinitions.width(type)
    val height: Int get() = InventoryDefinitions.height(type)

    fun totalDamageBonus(): Int = InventoryDefinitions.baseDamageBonus(type) + damageBonus
}

sealed class AddItemResult {
    data class Added(val item: InventoryItem) : AddItemResult()
    data object Stacked : AddItemResult()
    data object InventoryFull : AddItemResult()
}

class InventoryGrid(
    val columns: Int,
    val rows: Int,
    val items: MutableList<InventoryItem> = mutableListOf(),
    var nextItemId: Int = 1,
) {
    fun find(itemId: Int): InventoryItem? = items.find { it.id == itemId }

    fun allocateId(): Int = nextItemId++

    fun add(type: InventoryItemType, quantity: Int = 1, damageBonus: Int = 0): AddItemResult {
        if (InventoryDefinitions.isStackable(type)) {
            val existing = items.firstOrNull {
                it.type == type && it.quantity < InventoryDefinitions.maxStack(it.type)
            }
            if (existing != null) {
                val room = InventoryDefinitions.maxStack(type) - existing.quantity
                existing.quantity += quantity.coerceAtMost(room)
                return AddItemResult.Stacked
            }
        }

        val placement = findPlacement(type) ?: return AddItemResult.InventoryFull
        val item = InventoryItem(
            id = allocateId(),
            type = type,
            quantity = quantity.coerceAtMost(InventoryDefinitions.maxStack(type)),
            col = placement.first,
            row = placement.second,
            damageBonus = damageBonus,
        )
        items.add(item)
        return AddItemResult.Added(item)
    }

    /** Забирает патроны указанного типа из любых стаков в сетке. */
    fun consumeAmmo(amount: Int, ammoType: InventoryItemType): Int {
        if (amount <= 0) return 0
        var remaining = amount
        val ammoItems = items.filter { it.type == ammoType }.sortedBy { it.id }
        for (item in ammoItems) {
            if (remaining <= 0) break
            val take = item.quantity.coerceAtMost(remaining)
            item.quantity -= take
            remaining -= take
            if (item.quantity <= 0) items.remove(item)
        }
        return amount - remaining
    }

    fun totalAmmo(ammoType: InventoryItemType): Int =
        items.filter { it.type == ammoType }.sumOf { it.quantity }

    fun remove(itemId: Int): InventoryItem? {
        val item = find(itemId) ?: return null
        items.remove(item)
        return item
    }

    private fun findPlacement(type: InventoryItemType): Pair<Int, Int>? {
        val w = InventoryDefinitions.width(type)
        val h = InventoryDefinitions.height(type)
        for (row in 0 until rows - h + 1) {
            for (col in 0 until columns - w + 1) {
                if (canPlaceAt(type, col, row)) return col to row
            }
        }
        return null
    }

    private fun canPlaceAt(type: InventoryItemType, col: Int, row: Int): Boolean {
        val w = InventoryDefinitions.width(type)
        val h = InventoryDefinitions.height(type)
        for (dr in 0 until h) {
            for (dc in 0 until w) {
                if (isOccupied(col + dc, row + dr)) return false
            }
        }
        return true
    }

    private fun isOccupied(col: Int, row: Int): Boolean =
        items.any { item ->
            col in item.col until item.col + item.width &&
                row in item.row until item.row + item.height
        }
}
