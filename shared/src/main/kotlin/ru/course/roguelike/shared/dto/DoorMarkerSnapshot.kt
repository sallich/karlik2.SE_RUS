package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.ItemKind

/**
 * Метка приза над дверным проёмом незачищенной комнаты (issue #24).
 *
 * Подсказывает игроку, что ждёт за дверью. [kind] == null обозначает ключ
 * (отдельная от [ItemKind] сущность), иначе — вид предмета-приза.
 */
@Serializable
data class DoorMarkerSnapshot(
    val x: Float,
    val y: Float,
    val kind: ItemKind? = null,
)
