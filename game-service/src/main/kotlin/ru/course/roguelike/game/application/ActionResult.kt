package ru.course.roguelike.game.application

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerActionResponse

data class ActionResult(
    val response: PlayerActionResponse,
    val snapshot: GameSnapshot,
)
