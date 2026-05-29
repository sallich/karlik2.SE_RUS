package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.protocol.GameActions

/**
 * MCP-совместимые дискретные ходы → тот же FPS sync с фиксированным delta.
 */
class LegacyMovementCommand(
    private val action: String,
) : GameCommand {
    override val name: String = action

    override fun validate(session: GameSession): CommandValidation {
        if (action !in GameActions.MOVEMENT) {
            return CommandValidation(false, "Unknown action: $action")
        }
        return SyncInputCommand(inputFor(action)).validate(session)
    }

    override fun execute(session: GameSession): CommandExecutionResult =
        SyncInputCommand(inputFor(action)).execute(session)

    companion object {
        fun inputFor(action: String): InputSyncRequest = when (action) {
            GameActions.MOVE_NORTH -> InputSyncRequest(forward = true, deltaMs = 100)
            GameActions.MOVE_SOUTH -> InputSyncRequest(backward = true, deltaMs = 100)
            GameActions.MOVE_EAST -> InputSyncRequest(strafeRight = true, deltaMs = 100)
            GameActions.MOVE_WEST -> InputSyncRequest(strafeLeft = true, deltaMs = 100)
            else -> error("Unsupported movement action: $action")
        }
    }
}
