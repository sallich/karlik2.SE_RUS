package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.ItemKind

/**
 * Дверь незачищенной комнаты (issue #24): коричневый блок с иконкой приза в проёме.
 *
 * Подсказывает игроку, что ждёт за дверью. [kind] == null обозначает ключ
 * (отдельная от [ItemKind] сущность), иначе — вид предмета-приза.
 * [sealed] == true — герой заперт внутри в бою: дверь становится красной и блокирует выход.
 */
@Serializable
data class DoorMarkerSnapshot(
    val x: Float,
    val y: Float,
    val kind: ItemKind? = null,
    val sealed: Boolean = false,
)
