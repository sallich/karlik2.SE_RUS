package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.mobBehaviorFor
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Расстановка мобов по комнатам локации.
 *
 * В каждой комнате (кроме стартовой) — два вида мобов ([MobKind.MELEE] и
 * [MobKind.RANGED]) в количестве, зависящем от размера комнаты и препятствий.
 * Колонны и лифты затрудняют подход ближников, поэтому в таких комнатах
 * растёт доля дальнобойных мобов.
 */
object MobSpawner {
    const val MIN_MOBS_PER_KIND = RoomMobPlanner.MIN_MOBS_PER_KIND

    private const val MOB_SALT = 0x4D0B5FA00L

    fun spawnForLevel(
        session: GameSession,
        level: GeneratedLevel,
        seed: Long,
        occupied: Set<GridPos> = emptySet(),
    ) {
        if (skipAllMobs()) return
        val map = session.map
        val safeCells = MapConnectivity.reachableSafeFloorCells(map, level.playerSpawn)
        val random = Random(seed xor MOB_SALT)
        val startRoom = level.rooms.firstOrNull { it.contains(level.playerSpawn) }
        val usedCells = occupied.toMutableSet()

        level.rooms
            .filter { it != startRoom }
            .forEach { room -> spawnRoomMobs(session, room, map, safeCells, usedCells, random) }
        spawnLlmGuard(session)
    }

    fun createMob(session: GameSession, kind: MobKind, x: Float, y: Float): MobEntity {
        val behavior = mobBehaviorFor(kind)
        val id = session.allocateEntityId()
        return when (kind) {
            MobKind.MELEE -> MobEntity.MeleeMob(id, x, y, behavior)
            MobKind.RANGED -> MobEntity.RangedMob(id, x, y, behavior)
            MobKind.LLM_GUARD -> MobEntity.LlmGuardMob(id, x, y, behavior)
        }
    }

    private fun spawnRoomMobs(
        session: GameSession,
        room: Room,
        map: TileMap,
        safeCells: Set<GridPos>,
        usedCells: MutableSet<GridPos>,
        random: Random,
    ) {
        val metrics = RoomMobPlanner.roomLayoutMetrics(room, map, safeCells)
        val available = metrics.walkableCells.filter { it !in usedCells && it != room.center }
        if (available.size < 2) return

        val plan = RoomMobPlanner.capPlan(RoomMobPlanner.mobPlanForRoom(metrics), available.size)
        val cells = MobSpawnCells.pick(available, plan.total, random)
        if (cells.size < 2) return

        placeKind(session, MobKind.MELEE, cells, plan.meleeCount, usedCells)
        placeKind(session, MobKind.RANGED, cells, plan.rangedCount, usedCells)
    }

    private fun placeKind(
        session: GameSession,
        kind: MobKind,
        cells: List<GridPos>,
        count: Int,
        usedCells: MutableSet<GridPos>,
    ) {
        cells.filter { it !in usedCells }.take(count).forEach { cell ->
            placeMob(session, kind, cell, usedCells)
        }
    }

    private fun placeMob(
        session: GameSession,
        kind: MobKind,
        cell: GridPos,
        usedCells: MutableSet<GridPos>,
    ) {
        session.mobs.add(createMob(session, kind, cell.x + 0.5f, cell.y + 0.5f))
        usedCells.add(cell)
    }

    private fun spawnLlmGuard(session: GameSession) {
        if (skipLlmMob()) return
        val gate = session.exitGate ?: return
        val map = session.activeMap
        val candidates = listOf(
            GridPos(gate.x - 1, gate.y),
            GridPos(gate.x + 1, gate.y),
            GridPos(gate.x, gate.y - 1),
            GridPos(gate.x, gate.y + 1),
        )
        val spot = candidates.firstOrNull { map.get(it) == TileType.FLOOR } ?: gate
        session.mobs.add(
            createMob(session, MobKind.LLM_GUARD, spot.x + 0.5f, spot.y + 0.5f),
        )
    }

    private fun skipLlmMob(): Boolean =
        System.getenv("SKIP_LLM_MOB") == "true" || System.getProperty("SKIP_LLM_MOB") == "true"

    private fun skipAllMobs(): Boolean =
        System.getenv("SKIP_MOBS") == "true" || System.getProperty("SKIP_MOBS") == "true"
}
