package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.mobBehaviorFor
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
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

    fun createMob(session: GameSession, kind: MobKind, x: Float, y: Float, aggroRoom: Room): MobEntity {
        val behavior = mobBehaviorFor(kind)
        val id = session.allocateEntityId()
        return when (kind) {
            MobKind.MELEE -> MobEntity.MeleeMob(id, x, y, behavior, aggroRoom)
            MobKind.RANGED -> MobEntity.RangedMob(id, x, y, behavior, aggroRoom)
            MobKind.LLM_GUARD -> MobEntity.LlmGuardMob(id, x, y, behavior, aggroRoom)
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

        placeKindSpaced(session, map, room, MobKind.MELEE, available, plan.meleeCount, usedCells, random)
        val remaining = available.filter { it !in usedCells }
        placeKindSpaced(session, map, room, MobKind.RANGED, remaining, plan.rangedCount, usedCells, random)
    }

    @Suppress("LongParameterList")
    private fun placeKindSpaced(
        session: GameSession,
        map: TileMap,
        aggroRoom: Room,
        kind: MobKind,
        candidates: List<GridPos>,
        count: Int,
        usedCells: MutableSet<GridPos>,
        random: Random,
    ) {
        if (count <= 0 || candidates.isEmpty()) return
        MobSpawnCells.pick(candidates, count, random, avoid = usedCells.toList()).forEach { cell ->
            placeMob(session, map, kind, cell, aggroRoom, usedCells)
        }
    }

    private fun placeMob(
        session: GameSession,
        map: TileMap,
        kind: MobKind,
        cell: GridPos,
        aggroRoom: Room,
        usedCells: MutableSet<GridPos>,
    ) {
        val tile = map.get(cell)
        if (tile == null || !tile.walkable) return
        val x = cell.x + 0.5f
        val y = cell.y + 0.5f
        val mob = createMob(session, kind, x, y, aggroRoom)
        val circle = EntityCollision.Circle(x, y, CombatConstants.MOB_RADIUS)
        if (EntityCollision.overlapsMovement(map, circle, mob.z)) return
        session.mobs.add(mob)
        usedCells.add(cell)
    }

    private fun spawnLlmGuard(session: GameSession) {
        if (skipLlmMob()) return
        val gate = session.exitGate ?: return
        val aggroRoom = session.bossRoom ?: Room(gate.x, gate.y, 1, 1)
        val map = session.activeMap
        val candidates = listOf(
            GridPos(gate.x - 1, gate.y),
            GridPos(gate.x + 1, gate.y),
            GridPos(gate.x, gate.y - 1),
            GridPos(gate.x, gate.y + 1),
        )
        val spot = candidates.firstOrNull { map.get(it) == TileType.FLOOR } ?: gate
        placeMob(session, map, MobKind.LLM_GUARD, spot, aggroRoom, mutableSetOf())
    }

    private fun skipLlmMob(): Boolean =
        System.getenv("SKIP_LLM_MOB") == "true" || System.getProperty("SKIP_LLM_MOB") == "true"

    private fun skipAllMobs(): Boolean =
        System.getenv("SKIP_MOBS") == "true" || System.getProperty("SKIP_MOBS") == "true"
}
