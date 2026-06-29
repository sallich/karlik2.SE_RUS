package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest

class SyncInputCommand(
    private val input: InputSyncRequest,
) : GameCommand {
    override val name: String = NAME

    override fun validate(session: GameSession): CommandValidation =
        if (session.playerHp <= 0) {
            CommandValidation(false, "Player is dead")
        } else {
            CommandValidation(true)
        }

    override fun execute(session: GameSession): CommandExecutionResult {
        val events = mutableListOf<GameEvent>(GameEvent.CommandExecuted(name, accepted = true))
        events.addAll(SyncInputPipeline.runPlayer(session, input))
        return CommandExecutionResult(
            accepted = true,
            message = "sync ok",
            events = events,
        )
    }

    companion object {
        const val NAME = "sync_input"
    }
}
