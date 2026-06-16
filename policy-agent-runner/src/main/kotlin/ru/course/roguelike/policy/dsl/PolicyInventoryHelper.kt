package ru.course.roguelike.policy.dsl

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.ItemKind

object PolicyInventoryHelper {
    fun isInventoryFull(snapshot: GameSnapshot): Boolean {
        val inv = snapshot.player.inventory ?: return false
        val capacity = inv.columns * inv.rows
        if (capacity <= 0) return false
        val used = inv.items.sumOf { it.width * it.height }
        return used >= capacity
    }

    fun needsWeapon(snapshot: GameSnapshot): Boolean {
        if (!snapshot.player.equippedWeaponType.isNullOrBlank()) return false
        val hotbar = snapshot.player.hotbar ?: return true
        return hotbar.slots.none { it != null }
    }

    fun hasVisibleWeaponItem(snapshot: GameSnapshot): Boolean =
        snapshot.items.any {
            it.kind == ItemKind.WEAPON_PISTOL || it.kind == ItemKind.WEAPON_SHOTGUN
        }

    fun summary(snapshot: GameSnapshot): String {
        val inv = snapshot.player.inventory
        val weapon = snapshot.player.equippedWeaponType ?: "none"
        val ammo = "${snapshot.player.ammo}/${snapshot.player.maxAmmo}"
        val slots = inv?.let { "${it.items.size} items in ${it.columns}x${it.rows}" } ?: "no inventory"
        return "weapon=$weapon ammo=$ammo $slots full=${isInventoryFull(snapshot)}"
    }
}
