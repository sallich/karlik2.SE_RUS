package ru.course.roguelike.shared.model

/** Параметры сетки инвентаря и hotbar. */
object InventoryConstants {
    const val GRID_COLUMNS = 4
    const val GRID_ROWS = 3
    const val HOTBAR_SLOTS = 2

    /** Патронов в одной пачке на карте и в одном стаке инвентаря. */
    const val AMMO_STACK_SIZE = 20

    /** Стартовый заряд магазина (полный магазин пистолета). */
    const val STARTING_LOADED_AMMO = 12

    /** Сколько пачек патронов даётся при старте забега. */
    const val STARTING_AMMO_STACKS = 1

    /** HP, восстанавливаемые аптечкой. */
    const val HEALTH_KIT_RESTORE = 30

    /** Дополнительный урон от подобранного оружия на карте. */
    const val PICKUP_WEAPON_DAMAGE_BONUS = 5
}
