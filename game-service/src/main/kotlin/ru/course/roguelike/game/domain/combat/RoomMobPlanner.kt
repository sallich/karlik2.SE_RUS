package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/** Расчёт плотности и состава мобов для одной комнаты. */
internal object RoomMobPlanner {
    const val MIN_MOBS_PER_KIND = 1

    private const val MOB_DENSITY = 0.08f
    private const val COLUMN_AREA_PENALTY = 0.6f
    private const val BASE_RANGED_SHARE = 0.35f
    private const val COLUMN_RANGED_BOOST = 0.45f
    private const val ELEVATOR_RANGED_BOOST = 0.12f
    private const val MIN_RANGED_SHARE = 0.25f
    private const val MAX_RANGED_SHARE = 0.72f

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
        for (pos in roomCellPositions(room)) {
            when (map.get(pos)) {
                TileType.COLUMN -> columns++
                TileType.ELEVATOR -> elevators++
                else -> if (pos in safeCells) walkable.add(pos)
            }
        }
        return RoomLayoutMetrics(room, walkable, columns, elevators)
    }

    fun capPlan(plan: RoomMobPlan, availableCells: Int): RoomMobPlan {
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

    private fun roomCellPositions(room: Room): List<GridPos> {
        val cells = ArrayList<GridPos>(room.area)
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                cells.add(GridPos(x, y))
            }
        }
        return cells
    }
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
