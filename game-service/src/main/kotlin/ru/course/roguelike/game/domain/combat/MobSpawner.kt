package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.mobBehaviorFor
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.TileType
import kotlin.math.hypot

/** Расстановка тестовых мобов при старте сессии. */
object MobSpawner {
    private const val MIN_PLAYER_DISTANCE = 4f

    fun spawnStarterPack(session: GameSession) {
        if (skipAllMobs()) return
        val map = session.activeMap
        val spots = findSpawnSpots(
            map,
            session.playerPose.x,
            session.playerPose.y,
            count = 2,
        )
        if (spots.isNotEmpty()) {
            session.mobs.add(createMob(session, MobKind.MELEE, spots[0].x + 0.5f, spots[0].y + 0.5f))
        }
        if (spots.size > 1) {
            session.mobs.add(createMob(session, MobKind.RANGED, spots[1].x + 0.5f, spots[1].y + 0.5f))
        }
        spawnLlmGuard(session)
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

    fun createMob(session: GameSession, kind: MobKind, x: Float, y: Float): MobEntity {
        val behavior = mobBehaviorFor(kind)
        val id = session.allocateEntityId()
        return when (kind) {
            MobKind.MELEE -> MobEntity.MeleeMob(id, x, y, behavior)
            MobKind.RANGED -> MobEntity.RangedMob(id, x, y, behavior)
            MobKind.LLM_GUARD -> MobEntity.LlmGuardMob(id, x, y, behavior)
        }
    }

    private fun findSpawnSpots(
        map: TileMap,
        playerX: Float,
        playerY: Float,
        count: Int,
    ): List<GridPos> {
        val spots = mutableListOf<GridPos>()
        for (pos in floorCells(map)) {
            if (spots.size >= count) break
            if (isEligibleSpawn(map, pos, playerX, playerY, spots)) {
                spots.add(pos)
            }
        }
        return spots
    }

    private fun skipLlmMob(): Boolean =
        System.getenv("SKIP_LLM_MOB") == "true" || System.getProperty("SKIP_LLM_MOB") == "true"

    private fun skipAllMobs(): Boolean =
        System.getenv("SKIP_MOBS") == "true" || System.getProperty("SKIP_MOBS") == "true"

    private fun floorCells(map: TileMap): List<GridPos> =
        (1 until map.height - 1).flatMap { y ->
            (1 until map.width - 1).mapNotNull { x ->
                GridPos(x, y).takeIf { map.get(GridPos(x, y)) == TileType.FLOOR }
            }
        }

    private fun isEligibleSpawn(
        map: TileMap,
        pos: GridPos,
        playerX: Float,
        playerY: Float,
        spots: List<GridPos>,
    ): Boolean {
        if (map.get(pos) != TileType.FLOOR) return false
        val wx = pos.x + 0.5f
        val wy = pos.y + 0.5f
        if (hypot((wx - playerX).toDouble(), (wy - playerY).toDouble()) < MIN_PLAYER_DISTANCE) {
            return false
        }
        return spots.none { spot ->
            hypot(
                (spot.x + 0.5f - wx).toDouble(),
                (spot.y + 0.5f - wy).toDouble(),
            ) < 3.0
        }
    }
}
