package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.ItemKind

/**
 * Коридорная печать комнаты ([TileType.ROOM_SEAL], issue #24).
 * Клиент рисует красную стену с иконкой приза по этим полям.
 */
@Serializable
data class DoorMarkerSnapshot(
    val x: Float,
    val y: Float,
    val kind: ItemKind? = null,
    val prizeIsKey: Boolean = false,
    val mobRoom: Boolean = false,
)
