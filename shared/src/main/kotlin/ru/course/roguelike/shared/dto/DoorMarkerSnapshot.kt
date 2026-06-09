package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.ItemKind

/**
 * Незабранная дверь комнаты на карте ([TileType.ROOM_DOOR], issue #24).
 * Клиент рисует коричневую стену двери с иконкой приза по этим полям.
 */
@Serializable
data class DoorMarkerSnapshot(
    val x: Float,
    val y: Float,
    val kind: ItemKind? = null,
    val prizeIsKey: Boolean = false,
    val mobRoom: Boolean = false,
)
