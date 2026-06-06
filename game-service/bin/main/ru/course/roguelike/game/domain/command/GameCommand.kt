package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession

interface GameCommand {
    val name: String

    fun validate(session: GameSession): CommandValidation

    fun execute(session: GameSession): CommandExecutionResult
}

data class CommandValidation(
    val ok: Boolean,
    val message: String = "",
)

data class CommandExecutionResult(
    val accepted: Boolean,
    val message: String,
    val events: List<GameEvent> = emptyList(),
)
