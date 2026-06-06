package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

/** Тип предмета в сетке инвентаря (RE-стиль). */
@Serializable
enum class InventoryItemType {
    PISTOL,
    SHOTGUN,
    PISTOL_AMMO,
    SHOTGUN_AMMO,
    HEALTH_KIT,
}

object InventoryDefinitions {
    fun width(type: InventoryItemType): Int = when (type) {
        InventoryItemType.PISTOL, InventoryItemType.SHOTGUN -> 2
        else -> 1
    }

    fun height(type: InventoryItemType): Int = when (type) {
        InventoryItemType.SHOTGUN -> 2
        else -> 1
    }

    fun maxStack(type: InventoryItemType): Int = when (type) {
        InventoryItemType.PISTOL_AMMO, InventoryItemType.SHOTGUN_AMMO -> InventoryConstants.AMMO_STACK_SIZE
        else -> 1
    }

    fun isWeapon(type: InventoryItemType): Boolean =
        type == InventoryItemType.PISTOL || type == InventoryItemType.SHOTGUN

    fun isAmmo(type: InventoryItemType): Boolean =
        type == InventoryItemType.PISTOL_AMMO || type == InventoryItemType.SHOTGUN_AMMO

    fun isConsumable(type: InventoryItemType): Boolean =
        isAmmo(type) || type == InventoryItemType.HEALTH_KIT

    fun isStackable(type: InventoryItemType): Boolean = isAmmo(type)

    fun ammoTypeForWeapon(weapon: InventoryItemType): InventoryItemType? = when (weapon) {
        InventoryItemType.PISTOL -> InventoryItemType.PISTOL_AMMO
        InventoryItemType.SHOTGUN -> InventoryItemType.SHOTGUN_AMMO
        else -> null
    }

    fun baseDamageBonus(type: InventoryItemType): Int = when (type) {
        InventoryItemType.PISTOL -> 0
        InventoryItemType.SHOTGUN -> 10
        else -> 0
    }

    fun displayName(type: InventoryItemType): String = when (type) {
        InventoryItemType.PISTOL -> "Pistol"
        InventoryItemType.SHOTGUN -> "Shotgun"
        InventoryItemType.PISTOL_AMMO -> "9mm"
        InventoryItemType.SHOTGUN_AMMO -> "12ga"
        InventoryItemType.HEALTH_KIT -> "Medkit"
    }

    fun ammoCost(type: InventoryItemType): Int = when (type) {
        InventoryItemType.SHOTGUN -> 2
        InventoryItemType.PISTOL -> 1
        else -> 0
    }

    /** Ёмкость магазина в единицах боезапаса (совпадает с [ammoCost] за выстрел). */
    fun magazineCapacity(type: InventoryItemType): Int = when (type) {
        InventoryItemType.PISTOL -> 12
        InventoryItemType.SHOTGUN -> 8
        else -> 0
    }

    fun attackCooldownMs(type: InventoryItemType): Int = when (type) {
        InventoryItemType.SHOTGUN -> 850
        InventoryItemType.PISTOL -> 450
        else -> 450
    }

    fun pelletCount(type: InventoryItemType): Int = when (type) {
        InventoryItemType.SHOTGUN -> 5
        else -> 1
    }

    fun spreadRadians(type: InventoryItemType): Float = when (type) {
        InventoryItemType.SHOTGUN -> 0.22f
        InventoryItemType.PISTOL -> 0.04f
        else -> 0.04f
    }

    fun pelletDamage(type: InventoryItemType, totalDamage: Int): Int = when (type) {
        InventoryItemType.SHOTGUN -> (totalDamage * 0.4f).toInt().coerceAtLeast(1)
        else -> totalDamage
    }

    fun fromItemKind(kind: ItemKind): InventoryItemType? = when (kind) {
        ItemKind.WEAPON_PISTOL -> InventoryItemType.PISTOL
        ItemKind.WEAPON_SHOTGUN -> InventoryItemType.SHOTGUN
        ItemKind.AMMO_PISTOL -> InventoryItemType.PISTOL_AMMO
        ItemKind.AMMO_SHOTGUN -> InventoryItemType.SHOTGUN_AMMO
        else -> null
    }

    fun isManualPickup(kind: ItemKind): Boolean =
        kind == ItemKind.WEAPON_PISTOL || kind == ItemKind.WEAPON_SHOTGUN
}
