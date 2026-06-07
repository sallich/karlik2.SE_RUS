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
import kotlin.math.hypot
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
    private const val MIN_MOB_SEPARATION = 2.5f

    /** Минимум мобов каждого вида в комнате. */
    const val MIN_MOBS_PER_KIND = 1

    /** Базовая плотность мобов на проходимую клетку комнаты. */
    private const val MOB_DENSITY = 0.14f

    /** Каждая колонна уменьшает «эффективную» площадь для расчёта общего числа. */
    private const val COLUMN_AREA_PENALTY = 0.6f

    /** Базовая доля дальнобойных мобов без препятствий. */
    private const val BASE_RANGED_SHARE = 0.35f

    /** Насколько колонны смещают баланс в сторону дальнобойных. */
    private const val COLUMN_RANGED_BOOST = 0.45f

    /** Дополнительный сдвиг за каждый лифт в комнате. */
    private const val ELEVATOR_RANGED_BOOST = 0.12f

    private const val MIN_RANGED_SHARE = 0.25f
    private const val MAX_RANGED_SHARE = 0.72f

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

        for (room in level.rooms) {
            if (room == startRoom) continue
            val metrics = roomLayoutMetrics(room, map, safeCells)
            val available = metrics.walkableCells
                .filter { it !in usedCells && it != room.center }
            if (available.size < 2) continue

            val plan = capPlan(mobPlanForRoom(metrics), available.size)
            val cells = pickSpawnCells(available, plan.total, random)
            if (cells.size < 2) continue

            placeKind(session, MobKind.MELEE, cells, plan.meleeCount, usedCells)
            placeKind(session, MobKind.RANGED, cells, plan.rangedCount, usedCells)
        }
        spawnLlmGuard(session)
    }

    /** План числа мобов двух видов для одной комнаты (для тестов и отладки). */
    fun mobPlanForRoom(metrics: RoomLayoutMetrics): RoomMobPlan {
        val effectiveArea = (metrics.walkableCount - metrics.columnCount * COLUMN_AREA_PENALTY)
            .coerceAtLeast(MIN_MOBS_PER_KIND * 2f)
        val total = (effectiveArea * MOB_DENSITY).toInt().coerceAtLeast(MIN_MOBS_PER_KIND * 2)

        val columnRatio = metrics.columnCount.toFloat() / metrics.room.area.coerceAtLeast(1)
        val rangedShare = (
            BASE_RANGED_SHARE +
                columnRatio * COLUMN_RANGED_BOOST +
                metrics.elevatorCount * ELEVATOR_RANGED_BOOST
            ).coerceIn(MIN_RANGED_SHARE, MAX_RANGED_SHARE)

        val ranged = (total * rangedShare).toInt().coerceAtLeast(MIN_MOBS_PER_KIND)
        val melee = (total - ranged).coerceAtLeast(MIN_MOBS_PER_KIND)
        return RoomMobPlan(melee, ranged)
    }

    fun roomLayoutMetrics(
        room: Room,
        map: TileMap,
        safeCells: Set<GridPos>,
    ): RoomLayoutMetrics {
        val walkable = mutableListOf<GridPos>()
        var columns = 0
        var elevators = 0
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                val pos = GridPos(x, y)
                when (map.get(pos)) {
                    TileType.COLUMN -> columns++
                    TileType.ELEVATOR -> elevators++
                    else -> if (pos in safeCells) walkable.add(pos)
                }
            }
        }
        return RoomLayoutMetrics(room, walkable, columns, elevators)
    }

    private fun capPlan(plan: RoomMobPlan, availableCells: Int): RoomMobPlan {
        if (availableCells <= 0) return RoomMobPlan(0, 0)
        if (availableCells == 1) return RoomMobPlan(1, 0)
        val total = plan.total.coerceAtMost(availableCells).coerceAtLeast(2)
        var ranged = plan.rangedCount.coerceAtMost(total - 1).coerceAtLeast(1)
        var melee = (total - ranged).coerceAtLeast(1)
        if (melee + ranged > total) {
            melee = total - ranged
        }
        return RoomMobPlan(melee, ranged)
    }

    private fun placeKind(
        session: GameSession,
        kind: MobKind,
        cells: List<GridPos>,
        count: Int,
        usedCells: MutableSet<GridPos>,
    ) {
        var placed = 0
        for (cell in cells) {
            if (placed >= count) break
            if (cell in usedCells) continue
            placeMob(session, kind, cell, usedCells)
            placed++
        }
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

    private fun placeMob(
        session: GameSession,
        kind: MobKind,
        cell: GridPos,
        usedCells: MutableSet<GridPos>,
    ) {
        session.mobs.add(createMob(session, kind, cell.x + 0.5f, cell.y + 0.5f))
        usedCells.add(cell)
    }

    private fun pickSpawnCells(
        candidates: List<GridPos>,
        count: Int,
        random: Random,
    ): List<GridPos> {
        val pool = candidates.shuffled(random)
        val picked = mutableListOf<GridPos>()
        for (pos in pool) {
            if (picked.size >= count) break
            if (isFarEnoughFrom(pos, picked)) {
                picked.add(pos)
            }
        }
        if (picked.size < count) {
            for (pos in pool) {
                if (picked.size >= count) break
                if (pos !in picked) picked.add(pos)
            }
        }
        return picked
    }

    private fun isFarEnoughFrom(pos: GridPos, others: List<GridPos>): Boolean {
        val wx = pos.x + 0.5f
        val wy = pos.y + 0.5f
        return others.all { other ->
            hypot(
                (other.x + 0.5f - wx).toDouble(),
                (other.y + 0.5f - wy).toDouble(),
            ) >= MIN_MOB_SEPARATION
        }
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

    private const val MOB_SALT = 0x4D0B5FA00L
}

data class RoomLayoutMetrics(
    val room: Room,
    val walkableCells: List<GridPos>,
    val columnCount: Int,
    val elevatorCount: Int,
) {
    val walkableCount: Int get() = walkableCells.size
}

data class RoomMobPlan(
    val meleeCount: Int,
    val rangedCount: Int,
) {
    val total: Int get() = meleeCount + rangedCount
}
