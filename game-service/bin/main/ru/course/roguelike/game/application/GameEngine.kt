package ru.course.roguelike.game.application

import ru.course.roguelike.game.domain.combat.MobSpawner
import ru.course.roguelike.game.domain.command.CommandDispatcher
import ru.course.roguelike.game.domain.command.GameCommand
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.event.GameEventBus
import ru.course.roguelike.game.domain.event.GameEventListener
import ru.course.roguelike.game.domain.inventory.InventorySystem
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.LevelGenerator
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.game.domain.session.AgentCompanionSpawner
import ru.course.roguelike.game.domain.session.ExitGatePlacer
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.ItemPickup
import ru.course.roguelike.game.domain.session.ItemSpawner
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
    private val sessionLocks = ConcurrentHashMap<String, Any>()

    init {
        eventBus.subscribe(loggingListener)
    }

    fun createSession(seed: Long?, twoLevel: Boolean = false, coopAgent: Boolean = false): GameSnapshot {
        val resolvedSeed = seed ?: Random.nextLong()
        val sessionId = UUID.randomUUID().toString()
        val session = if (twoLevel) {
            buildTwoLevelSession(sessionId, resolvedSeed, coopAgent)
        } else {
            buildSession(sessionId, resolvedSeed, coopAgent)
        }
        InventorySystem.initialize(session)
        sessions[sessionId] = session
        MobSpawner.spawnStarterPack(session)
        eventBus.publish(listOf(GameEvent.SessionCreated(sessionId, resolvedSeed)))
        return session.toSnapshot()
    }

    private fun buildSession(sessionId: String, seed: Long, coopAgent: Boolean): GameSession {
        val level = levelGenerator.generate(seed)
        val progress = setupProgress(level, seed)
        val playerSpawn = level.playerSpawn
        return GameSession(
            sessionId = sessionId,
            seed = seed,
            phase = SessionPhase.EXPLORATION,
            map = progress.map,
            playerPose = PlayerPose.fromGridCell(playerSpawn),
            agentPose = if (coopAgent) {
                AgentCompanionSpawner.spawnBeside(progress.map, playerSpawn)
            } else {
                null
            },
            keyPickups = progress.keys,
            itemPickups = progress.items,
            nextItemId = progress.items.size,
            bossRoom = progress.bossRoom,
            exitGate = progress.exitGate,
        )
    }

    private fun buildTwoLevelSession(sessionId: String, seed: Long, coopAgent: Boolean): GameSession {
        val dungeon = TwoLevelLabyrinthGenerator.generate(seed)
        val ground = dungeon.levels[0]
        val progress = setupProgress(ground, seed)
        val playerSpawn = ground.playerSpawn
        return GameSession(
            sessionId = sessionId,
            seed = seed,
            phase = SessionPhase.EXPLORATION,
            map = progress.map,
            playerPose = PlayerPose.fromGridCell(playerSpawn),
            agentPose = if (coopAgent) {
                AgentCompanionSpawner.spawnBeside(progress.map, playerSpawn)
            } else {
                null
            },
            secondLevel = dungeon.levels[1].map,
            keyPickups = progress.keys,
            itemPickups = progress.items,
            nextItemId = progress.items.size,
            bossRoom = progress.bossRoom,
            exitGate = progress.exitGate,
        )
    }

    private data class ProgressSetup(
        val map: TileMap,
        val keys: MutableList<KeyPickup>,
        val items: MutableList<ItemPickup>,
        val bossRoom: Room?,
        val exitGate: GridPos?,
    )

    private fun setupProgress(level: GeneratedLevel, seed: Long): ProgressSetup {
        val boss = KeySpawner.bossRoomOf(level)
        val keys = KeySpawner.spawn(level, seed).toMutableList()
        val occupied = keys.map { GridPos(it.x.toInt(), it.y.toInt()) }.toSet()
        val items = ItemSpawner.spawn(level, seed, occupied).toMutableList()
        if (boss == null) {
            return ProgressSetup(level.map, keys, items, null, null)
        }
        val (mapWithGate, exitGate) = ExitGatePlacer.place(level, boss)
        return ProgressSetup(mapWithGate, keys, items, boss, exitGate)
    }

    fun getSnapshot(sessionId: String): GameSnapshot? = sessions[sessionId]?.toSnapshot()

    fun syncInput(sessionId: String, input: InputSyncRequest): ActionResult? {
        val command = if (input.actor == ACTOR_AGENT) {
            ru.course.roguelike.game.domain.command.AgentSyncInputCommand(input)
        } else {
            commandDispatcher.syncCommand(input.copy(actor = ACTOR_PLAYER))
        }
        return dispatch(sessionId, command)
    }

    fun applyAction(sessionId: String, action: String, actor: String = ACTOR_PLAYER): ActionResult? {
        val session = sessions[sessionId] ?: return null
        val movementInput = ru.course.roguelike.game.domain.command.LegacyMovementCommand.inputFor(action)
        val command = if (actor == ACTOR_AGENT) {
            ru.course.roguelike.game.domain.command.AgentSyncInputCommand(movementInput)
        } else {
            commandDispatcher.commandFromAction(action, session)
                ?: return ActionResult(
                    PlayerActionResponse(false, "Unknown action: $action"),
                    session.toSnapshot(),
                )
        }
        return dispatch(sessionId, command)
    }

    private fun dispatch(sessionId: String, command: GameCommand): ActionResult? {
        val session = sessions[sessionId] ?: return null
        synchronized(sessionLocks.computeIfAbsent(sessionId) { Any() }) {
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
    }

    companion object {
        const val ACTOR_PLAYER = "player"
        const val ACTOR_AGENT = "agent"
        private val loggingListener = GameEventListener { event ->
            if (event is GameEvent.PhaseChanged) {
                org.slf4j.LoggerFactory.getLogger(GameEngine::class.java)
                    .info("Phase {} -> {}", event.from, event.to)
            }
        }
    }
}
