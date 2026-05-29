package ru.course.roguelike.game.application

import ru.course.roguelike.game.domain.command.CommandDispatcher
import ru.course.roguelike.game.domain.command.GameCommand
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.event.GameEventListener
import ru.course.roguelike.game.domain.level.LevelGenerator
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.infrastructure.level.LevelGeneratorFactory
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Фасад приложения: сессии, оркестрация команд, снимки для API/MCP/клиента.
 */
class GameEngine(
    private val levelGenerator: LevelGenerator = LevelGeneratorFactory.create(),
    private val eventBus: GameEventBus = GameEventBus(),
    private val commandDispatcher: CommandDispatcher = CommandDispatcher(eventBus = eventBus),
) {
    private val sessions = ConcurrentHashMap<String, GameSession>()

    init {
        eventBus.subscribe(loggingListener)
    }

    fun createSession(seed: Long?): GameSnapshot {
        val resolvedSeed = seed ?: Random.nextLong()
        val sessionId = UUID.randomUUID().toString()
        val level = levelGenerator.generate(resolvedSeed)
        val session = GameSession(
            sessionId = sessionId,
            seed = resolvedSeed,
            phase = SessionPhase.EXPLORATION,
            map = level.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
        )
        sessions[sessionId] = session
        eventBus.publish(listOf(GameEvent.SessionCreated(sessionId, resolvedSeed)))
        return session.toSnapshot()
    }

    fun getSnapshot(sessionId: String): GameSnapshot? = sessions[sessionId]?.toSnapshot()

    fun syncInput(sessionId: String, input: InputSyncRequest): ActionResult? =
        dispatch(sessionId, commandDispatcher.syncCommand(input))

    fun applyAction(sessionId: String, action: String): ActionResult? {
        val session = sessions[sessionId] ?: return null
        val command = commandDispatcher.commandFromAction(action, session)
            ?: return ActionResult(
                PlayerActionResponse(false, "Unknown action: $action"),
                session.toSnapshot(),
            )
        return dispatch(sessionId, command)
    }

    private fun dispatch(sessionId: String, command: GameCommand): ActionResult? {
        val session = sessions[sessionId] ?: return null
        val result = commandDispatcher.dispatch(session, command)
        return ActionResult(
            response = PlayerActionResponse(
                accepted = result.accepted,
                message = result.message,
                snapshot = session.toSnapshot(),
            ),
            snapshot = session.toSnapshot(),
        )
    }

    companion object {
        private val loggingListener = GameEventListener { event ->
            if (event is GameEvent.PhaseChanged) {
                org.slf4j.LoggerFactory.getLogger(GameEngine::class.java)
                    .info("Phase {} -> {}", event.from, event.to)
            }
        }
    }
}
