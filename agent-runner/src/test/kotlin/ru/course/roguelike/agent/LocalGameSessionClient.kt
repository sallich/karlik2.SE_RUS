package ru.course.roguelike.agent

import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.mcp.client.GameSessionPort
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse

class LocalGameSessionClient(
    private val engine: GameEngine,
) : GameSessionPort {
    override suspend fun createSession(seed: Long?, twoLevel: Boolean, coopAgent: Boolean): GameSnapshot =
        engine.createSession(seed, twoLevel, coopAgent)

    override suspend fun observe(sessionId: String): GameSnapshot =
        engine.getSnapshot(sessionId) ?: error("Session not found: $sessionId")

    override suspend fun applyAction(sessionId: String, action: String, actor: String): PlayerActionResponse =
        engine.applyAction(sessionId, action, actor)?.response
            ?: PlayerActionResponse(accepted = false, message = "Session not found")

    override suspend fun sync(sessionId: String, input: InputSyncRequest, actor: String): PlayerActionResponse =
        engine.syncInput(sessionId, input.copy(actor = actor))?.response
            ?: PlayerActionResponse(accepted = false, message = "Session not found")
}
