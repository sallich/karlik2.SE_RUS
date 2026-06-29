package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class InventoryItemSnapshot(
    val id: Int,
    val type: String,
    val quantity: Int,
    val col: Int,
    val row: Int,
    val width: Int,
    val height: Int,
    val damageBonus: Int = 0,
    val displayName: String = "",
)

@Serializable
data class InventorySnapshot(
    val columns: Int,
    val rows: Int,
    val items: List<InventoryItemSnapshot> = emptyList(),
)

@Serializable
data class HotbarSnapshot(
    /** Id предметов в слотах hotbar (null — пусто). */
    val slots: List<Int?>,
    /** Id экипированного оружия. */
    val equippedItemId: Int? = null,
    /** Активный слот hotbar (0 или 1). */
    val selectedSlot: Int = 0,
)
