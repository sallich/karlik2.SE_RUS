package ru.course.roguelike.game.domain.phase

import ru.course.roguelike.game.domain.command.AgentSyncInputCommand
import ru.course.roguelike.game.domain.command.CommandExecutionResult
import ru.course.roguelike.game.domain.command.CommandValidation
import ru.course.roguelike.game.domain.command.GameCommand
import ru.course.roguelike.game.domain.command.SyncInputCommand
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.protocol.GameActions

private fun resolveEndPhase(session: GameSession): SessionPhase? = when {
    session.playerHp <= 0 -> SessionPhase.GAME_OVER
    session.levelCompleted -> SessionPhase.LEVEL_COMPLETE
    else -> null
}

interface PhaseHandler {
    val phase: SessionPhase

    fun validateCommand(command: GameCommand, session: GameSession): CommandValidation

    fun onCommandExecuted(
        session: GameSession,
        command: GameCommand,
        result: CommandExecutionResult,
    ): SessionPhase? = null
}

class ExplorationPhaseHandler : PhaseHandler {
    override val phase: SessionPhase = SessionPhase.EXPLORATION

    override fun validateCommand(command: GameCommand, session: GameSession): CommandValidation =
        when {
            session.playerHp <= 0 -> CommandValidation(false, "Game over")
            session.levelCompleted -> CommandValidation(false, "Level already completed")
            else -> CommandValidation(true)
        }

    override fun onCommandExecuted(
        session: GameSession,
        command: GameCommand,
        result: CommandExecutionResult,
    ): SessionPhase? = resolveEndPhase(session)
}

class CombatPhaseHandler : PhaseHandler {
    override val phase: SessionPhase = SessionPhase.COMBAT

    override fun validateCommand(command: GameCommand, session: GameSession): CommandValidation {
        if (session.playerHp <= 0) return CommandValidation(false, "Game over")
        return when (command.name) {
            SyncInputCommand.NAME,
            AgentSyncInputCommand.NAME,
            in GameActions.ALL -> CommandValidation(true)
            else -> CommandValidation(false, "Action not allowed during combat: ${command.name}")
        }
    }

    override fun onCommandExecuted(
        session: GameSession,
        command: GameCommand,
        result: CommandExecutionResult,
    ): SessionPhase? = resolveEndPhase(session)
}

class ChoicePhaseHandler : PhaseHandler {
    override val phase: SessionPhase = SessionPhase.CHOICE

    override fun validateCommand(command: GameCommand, session: GameSession): CommandValidation =
        CommandValidation(false, "Resolve room choice before moving (choose_room not implemented)")
}

class HubPhaseHandler : PhaseHandler {
    override val phase: SessionPhase = SessionPhase.HUB

    override fun validateCommand(command: GameCommand, session: GameSession): CommandValidation =
        when (command.name) {
            SyncInputCommand.NAME -> CommandValidation(false, "Movement disabled in hub")
            in GameActions.ALL -> CommandValidation(false, "Movement disabled in hub")
            else -> CommandValidation(false, "Unknown hub action: ${command.name}")
        }
}

class GameOverPhaseHandler : PhaseHandler {
    override val phase: SessionPhase = SessionPhase.GAME_OVER

    override fun validateCommand(command: GameCommand, session: GameSession): CommandValidation =
        CommandValidation(false, "Session ended")
}

class LevelCompletePhaseHandler : PhaseHandler {
    override val phase: SessionPhase = SessionPhase.LEVEL_COMPLETE

    override fun validateCommand(command: GameCommand, session: GameSession): CommandValidation =
        CommandValidation(false, "Session ended")
}
