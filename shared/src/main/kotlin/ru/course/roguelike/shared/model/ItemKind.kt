package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

/** Вид подбираемого предмета на локации. */
@Serializable
enum class ItemKind {
    HEALTH,
    EXPERIENCE,
    WEAPON_PISTOL,
    WEAPON_SHOTGUN,
    AMMO_PISTOL,
    AMMO_SHOTGUN,
}
