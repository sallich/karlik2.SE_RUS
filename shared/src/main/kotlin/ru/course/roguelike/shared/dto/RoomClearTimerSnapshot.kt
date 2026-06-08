package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable

/** Таймер зачистки комнаты, в которой сейчас находится игрок. */
@Serializable
data class RoomClearTimerSnapshot(
    val remainingMs: Long,
    val totalMs: Long,
    val reinforcementsTriggered: Boolean = false,
)
