package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable
import ru.course.roguelike.shared.model.ItemKind

@Serializable
data class KeySnapshot(
    val id: Int,
    val x: Float,
    val y: Float,
)

/** Снимок подбираемого предмета на локации (issue #9). */
@Serializable
data class ItemSnapshot(
    val id: Int,
    val kind: ItemKind,
    val x: Float,
    val y: Float,
)

@Serializable
data class BossRoomSnapshot(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
