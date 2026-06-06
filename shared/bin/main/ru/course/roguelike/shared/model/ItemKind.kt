package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

/** Вид подбираемого предмета на локации (issue #9). */
@Serializable
enum class ItemKind {
    /** Восстанавливает HP героя. */
    HEALTH,

    /** Даёт опыт (может поднять уровень). */
    EXPERIENCE,

    /** Постоянно увеличивает урон атаки. */
    WEAPON,

    /** Пополняет боезапас. */
    AMMO,
}
