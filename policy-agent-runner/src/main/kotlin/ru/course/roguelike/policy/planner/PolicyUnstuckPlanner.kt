package ru.course.roguelike.policy.planner

import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.dsl.PolicyExploreHelper
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.knowledge.PartialTileMap
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.protocol.GameActions
import kotlin.math.floor
import kotlin.math.hypot

/**
 * FPS-safe unstuck: pick a grid target (BFS toward known door / frontier) and step via [PolicyFpsNavigation].
 */
object PolicyUnstuckPlanner {
    fun plan(
        sessionId: String,
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        stepIndex: Int,
        lastBlockedMove: String?,
        avoidCellKey: String?,
        avoidTargetKeys: Set<String> = emptySet(),
        stuckAttempt: Int = 0,
        unstuckMode: String = PolicyParams.UNSTUCK_DOOR,
        visitedTrail: List<String> = emptyList(),
        pingPong: Boolean = false,
        strictFairPlay: Boolean = true,
        visited: Set<String> = emptySet(),
    ): ToolCallDecision {
        val map = knowledge.navigableMap(snapshot, strictFairPlay)
        val cell = playerCell(snapshot, map)
        val pose = snapshot.player.pose
        val target = pickTargetCell(
            snapshot = snapshot,
            knowledge = knowledge,
            map = map,
            cell = cell,
            stepIndex = stepIndex,
            lastBlockedMove = lastBlockedMove,
            avoidCellKey = avoidCellKey,
            avoidTargetKeys = avoidTargetKeys,
            stuckAttempt = stuckAttempt,
            unstuckMode = unstuckMode,
            visitedTrail = visitedTrail,
            pingPong = pingPong,
            visited = visited,
        )
        return PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, target, map)
    }

    internal fun pickTargetCell(
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        map: TileMap,
        cell: GridPos,
        stepIndex: Int,
        lastBlockedMove: String?,
        avoidCellKey: String?,
        avoidTargetKeys: Set<String> = emptySet(),
        stuckAttempt: Int = 0,
        unstuckMode: String = PolicyParams.UNSTUCK_DOOR,
        visitedTrail: List<String> = emptyList(),
        pingPong: Boolean = false,
        visited: Set<String> = emptySet(),
    ): GridPos {
        val blockedNeighbor = blockedNeighborCell(cell, lastBlockedMove)
        val cornerTrap = PolicyFpsPathfinder.isCornerTrap(map, cell)

        if (pingPong && cornerTrap || unstuckMode == PolicyParams.UNSTUCK_RETREAT) {
            knowledge.lastJunctionCell(visitedTrail)?.let { junction ->
                val path = PolicyFpsPathfinder.path(map, cell, junction, allowVertical = true)
                if (path != null && path.size >= 2) {
                    val candidate = path[1]
                    if (isAllowedStep(cell, candidate, blockedNeighbor, avoidCellKey, avoidTargetKeys)) {
                        return candidate
                    }
                }
            }
        }

        // Ping-pong: break the oscillation deterministically by routing to the nearest cell we
        // have not stood on yet. The target is fixed and the unvisited set only shrinks, so we
        // make monotonic progress instead of bouncing between two cells at a wall.
        if (pingPong && visited.isNotEmpty()) {
            PolicyExploreHelper.stepTowardNearestUnvisited(map, cell, visited)?.let { return it }
        }

        if (unstuckMode == PolicyParams.UNSTUCK_FRONTIER) {
            val frontier = knowledge.frontierCells(snapshot)
            val goal = frontier.minByOrNull { hypot((it.x - cell.x).toDouble(), (it.y - cell.y).toDouble()) }
            if (goal != null) {
                val path = PolicyFpsPathfinder.path(map, cell, goal, allowVertical = true)
                if (path != null && path.size >= 2) {
                    val candidates = path.drop(1).filter { next ->
                        isAllowedStep(cell, next, blockedNeighbor, avoidCellKey, avoidTargetKeys)
                    }
                    if (candidates.isNotEmpty()) {
                        return candidates[stuckAttempt % candidates.size]
                    }
                }
            }
        }

        val door = AgentDoorHelper.nearestVisibleDoor(snapshot) ?: knowledge.nearestKnownDoor(snapshot)
        if (door != null && unstuckMode != PolicyParams.UNSTUCK_FRONTIER) {
            val dc = AgentDoorHelper.doorCell(door)
            val approachGoals = listOf(
                GridPos(dc.x - 1, dc.y),
                GridPos(dc.x + 1, dc.y),
                GridPos(dc.x, dc.y - 1),
                GridPos(dc.x, dc.y + 1),
            ).filter { isWalkable(map, it) }
            val goal = PolicyFpsPathfinder.nearestReachable(map, cell, approachGoals, allowVertical = true)
            if (goal != null) {
                val path = PolicyFpsPathfinder.path(map, cell, goal, allowVertical = true)
                if (path != null && path.size >= 2) {
                    val pathCandidates = path.drop(1).filter { next ->
                        isAllowedStep(cell, next, blockedNeighbor, avoidCellKey, avoidTargetKeys)
                    }
                    if (pathCandidates.isNotEmpty()) {
                        return pathCandidates[stuckAttempt % pathCandidates.size]
                    }
                }
            }
        }

        val open = walkableNeighbors(map, cell)
        val ranked = rankNeighbors(map, cell, open, door)
        val filtered = ranked.filter { neighbor ->
            isAllowedStep(cell, neighbor, blockedNeighbor, avoidCellKey, avoidTargetKeys)
        }
        // Before falling back to cycling neighbors (which can oscillate), try to route to the
        // nearest cell we have not stood on yet.
        if (filtered.isEmpty() && visited.isNotEmpty()) {
            PolicyExploreHelper.stepTowardNearestUnvisited(map, cell, visited)?.let { return it }
        }
        val pool = filtered.ifEmpty {
            ranked.filter { neighbor ->
                neighbor != blockedNeighbor && cellKey(neighbor) !in avoidTargetKeys
            }
        }.ifEmpty { ranked }

        return pool[stuckAttempt % pool.size]
    }

    private fun rankNeighbors(
        map: TileMap,
        cell: GridPos,
        neighbors: List<GridPos>,
        door: ru.course.roguelike.shared.dto.DoorMarkerSnapshot?,
    ): List<GridPos> {
        val doorCell = door?.let { AgentDoorHelper.doorCell(it) }
        return neighbors.sortedWith(
            compareByDescending<GridPos> { PolicyFpsPathfinder.navigableNeighbors(map, it).size }
                .thenBy { neighbor ->
                    if (doorCell == null) {
                        0.0
                    } else {
                        hypot(
                            (neighbor.x - doorCell.x).toDouble(),
                            (neighbor.y - doorCell.y).toDouble(),
                        )
                    }
                },
        )
    }

    private fun isAllowedStep(
        from: GridPos,
        to: GridPos,
        blockedNeighbor: GridPos?,
        avoidCellKey: String?,
        avoidTargetKeys: Set<String>,
    ): Boolean {
        if (blockedNeighbor != null && to == blockedNeighbor) return false
        if (avoidCellKey != null && cellKey(to) == avoidCellKey) return false
        if (cellKey(to) in avoidTargetKeys) return false
        if (from == to) return false
        return true
    }

    private fun blockedNeighborCell(cell: GridPos, lastBlockedMove: String?): GridPos? {
        if (lastBlockedMove.isNullOrBlank()) return null
        val action = when {
            lastBlockedMove.startsWith("move_") -> lastBlockedMove
            else -> "move_$lastBlockedMove"
        }
        return when (action) {
            GameActions.MOVE_NORTH -> GridPos(cell.x + 1, cell.y)
            GameActions.MOVE_SOUTH -> GridPos(cell.x - 1, cell.y)
            GameActions.MOVE_EAST -> GridPos(cell.x, cell.y + 1)
            GameActions.MOVE_WEST -> GridPos(cell.x, cell.y - 1)
            else -> null
        }
    }

    private fun walkableNeighbors(map: TileMap, cell: GridPos): List<GridPos> = buildList {
        for (offset in OFFSETS) {
            val next = GridPos(cell.x + offset.x, cell.y + offset.y)
            if (isWalkable(map, next)) add(next)
        }
    }

    internal fun resolvePlayerCell(snapshot: GameSnapshot, map: TileMap): GridPos =
        playerCell(snapshot, map)

    private fun playerCell(snapshot: GameSnapshot, map: TileMap): GridPos {
        val pose = snapshot.player.pose
        val primary = GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
        if (isWalkable(map, primary)) return primary
        return OFFSETS.map { GridPos(primary.x + it.x, primary.y + it.y) }
            .filter { isWalkable(map, it) }
            .minByOrNull {
                hypot((it.x + 0.5 - pose.x).toDouble(), (it.y + 0.5 - pose.y).toDouble())
            } ?: primary
    }

    private fun isWalkable(map: TileMap, pos: GridPos): Boolean {
        val tile = map.get(pos) ?: return false
        return tile.walkable || tile == TileType.EXIT_GATE
    }

    private fun cellKey(cell: GridPos): String = PartialTileMap.cellKey(cell)

    private val OFFSETS = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))
}
