package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest

/** Движение и interact кооп-агента (отдельная поза, не playerPose). */
class AgentSyncInputCommand(
    private val input: InputSyncRequest,
) : GameCommand {
    override val name: String = NAME

    override fun validate(session: GameSession): CommandValidation = when {
        session.agentPose == null -> CommandValidation(false, "No coop agent in session")
        session.playerHp <= 0 -> CommandValidation(false, "Player is dead")
        else -> CommandValidation(true)
    }

    override fun execute(session: GameSession): CommandExecutionResult {
        val events = mutableListOf<GameEvent>(GameEvent.CommandExecuted(name, accepted = true))
        events.addAll(SyncInputPipeline.runAgent(session, input))
        return CommandExecutionResult(
            accepted = true,
            message = "agent sync ok",
            events = events,
        )
    }

    companion object {
        const val NAME = "agent_sync_input"
    }
}
