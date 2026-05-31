package ru.course.roguelike.game.application

import ru.course.roguelike.game.domain.combat.MobSpawner
import ru.course.roguelike.game.domain.command.CommandDispatcher
import ru.course.roguelike.game.domain.command.GameCommand
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.event.GameEventListener
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.LevelGenerator
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.game.domain.session.ExitGatePlacer
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.KeyPickup
import ru.course.roguelike.game.domain.session.KeySpawner
import ru.course.roguelike.game.infrastructure.level.LevelGeneratorFactory
import ru.course.roguelike.game.infrastructure.level.TwoLevelLabyrinthGenerator
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
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

    fun createSession(seed: Long?, twoLevel: Boolean = false): GameSnapshot {
        val resolvedSeed = seed ?: Random.nextLong()
        val sessionId = UUID.randomUUID().toString()
        val session = if (twoLevel) {
            buildTwoLevelSession(sessionId, resolvedSeed)
        } else {
            buildSession(sessionId, resolvedSeed)
        }
        sessions[sessionId] = session
        MobSpawner.spawnStarterPack(session)
        eventBus.publish(listOf(GameEvent.SessionCreated(sessionId, resolvedSeed)))
        return session.toSnapshot()
    }

    private fun buildSession(sessionId: String, seed: Long): GameSession {
        val level = levelGenerator.generate(seed)
        val progress = setupProgress(level, seed)
        return GameSession(
            sessionId = sessionId,
            seed = seed,
            phase = SessionPhase.EXPLORATION,
            map = progress.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
            keyPickups = progress.keys,
            bossRoom = progress.bossRoom,
            exitGate = progress.exitGate,
        )
    }

    private fun buildTwoLevelSession(sessionId: String, seed: Long): GameSession {
        val dungeon = TwoLevelLabyrinthGenerator.generate(seed)
        val ground = dungeon.levels[0]
        val progress = setupProgress(ground, seed)
        return GameSession(
            sessionId = sessionId,
            seed = seed,
            phase = SessionPhase.EXPLORATION,
            map = progress.map,
            playerPose = PlayerPose.fromGridCell(ground.playerSpawn),
            secondLevel = dungeon.levels[1].map,
            keyPickups = progress.keys,
            bossRoom = progress.bossRoom,
            exitGate = progress.exitGate,
        )
    }

    private data class ProgressSetup(
        val map: TileMap,
        val keys: MutableList<KeyPickup>,
        val bossRoom: Room?,
        val exitGate: GridPos?,
    )

    private fun setupProgress(level: GeneratedLevel, seed: Long): ProgressSetup {
        val boss = KeySpawner.bossRoomOf(level)
        val keys = KeySpawner.spawn(level, seed).toMutableList()
        if (boss == null) {
            return ProgressSetup(level.map, keys, null, null)
        }
        val (mapWithGate, exitGate) = ExitGatePlacer.place(level, boss)
        return ProgressSetup(mapWithGate, keys, boss, exitGate)
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
