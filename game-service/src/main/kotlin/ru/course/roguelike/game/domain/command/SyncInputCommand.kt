package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.FpsMovementSystem

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
        session.playerPose = FpsMovementSystem.applyInput(session.map, session.playerPose, input)
        session.touchClock()
        return CommandExecutionResult(
            accepted = true,
            message = "sync ok",
            events = listOf(
                GameEvent.CommandExecuted(name, accepted = true),
                GameEvent.PlayerMoved(session.tick),
            ),
        )
    }

    companion object {
        const val NAME = "sync_input"
    }
}
