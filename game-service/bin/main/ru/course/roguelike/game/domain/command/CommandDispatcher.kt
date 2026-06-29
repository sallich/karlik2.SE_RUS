package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.phase.PhaseRegistry
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest

class CommandDispatcher(
    private val phaseRegistry: PhaseRegistry = PhaseRegistry(),
    private val eventBus: GameEventBus,
    private val commandRegistry: CommandRegistry = CommandRegistry.default(),
) {
    fun dispatch(session: GameSession, command: GameCommand): CommandExecutionResult {
        val phaseHandler = phaseRegistry.handlerFor(session.phase)
        val phaseValidation = phaseHandler.validateCommand(command, session)
        if (!phaseValidation.ok) {
            val rejected = CommandExecutionResult(false, phaseValidation.message)
            eventBus.publish(rejected.events)
            return rejected
        }

        val validation = command.validate(session)
        if (!validation.ok) {
            val rejected = CommandExecutionResult(false, validation.message)
            eventBus.publish(rejected.events)
            return rejected
        }

        val result = command.execute(session)
        val nextPhase = phaseHandler.onCommandExecuted(session, command, result)
        if (nextPhase != null && nextPhase != session.phase) {
            val from = session.phase
            session.phase = nextPhase
            val withTransition = result.copy(
                events = result.events + ru.course.roguelike.game.domain.event.GameEvent.PhaseChanged(from, nextPhase),
            )
            eventBus.publish(withTransition.events)
            return withTransition
        }

        eventBus.publish(result.events)
        return result
    }

    fun commandFromAction(action: String, session: GameSession): GameCommand? {
        if (action == SyncInputCommand.NAME) return null
        return commandRegistry.commandFor(action, session)
    }

    fun syncCommand(input: InputSyncRequest): GameCommand = SyncInputCommand(input)
}
