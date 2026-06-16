package ru.course.roguelike.policy.dsl

import ru.course.roguelike.policy.knowledge.PartialTileMap
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.policy.planner.PolicyFpsNavigation
import ru.course.roguelike.policy.planner.PolicyFpsPathfinder
import ru.course.roguelike.policy.planner.PolicyNavigation
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.GridPos
import kotlin.math.hypot

/**
 * Path-memory exploration: prefer corridor exits that were not visited yet (fair-play map).
 */
object PolicyExploreHelper {
    fun hasUnvisitedExit(
        snapshot: GameSnapshot,
        visited: Set<String>,
        knowledge: PlayerKnowledgeLayer,
        strictFairPlay: Boolean = true,
    ): Boolean =
        hasFrontier(knowledge, snapshot) ||
            openNeighborKeys(snapshot, knowledge, strictFairPlay).any { it !in visited }

    fun hasFrontier(knowledge: PlayerKnowledgeLayer, snapshot: GameSnapshot): Boolean =
        knowledge.frontierCells(snapshot).isNotEmpty()

    fun moveTowardUnvisited(
        snapshot: GameSnapshot,
        sessionId: String,
        visited: Set<String>,
        knowledge: PlayerKnowledgeLayer,
        stepIndex: Int,
        lastBlockedMove: String?,
        avoidPosKey: String? = null,
        exploreMode: String = PolicyParams.EXPLORE_UNVISITED,
        visitedTrail: List<String> = emptyList(),
        pingPong: Boolean = false,
        strictFairPlay: Boolean = true,
        context: ru.course.roguelike.policy.loop.PolicyContext? = null,
    ): ToolCallDecision {
        val map = knowledge.navigableMap(snapshot, strictFairPlay)
        val cell = playerCell(snapshot, map)
        val pose = snapshot.player.pose

        fun stepTo(next: GridPos) =
            PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, next, map)

        // LLM-selected explicit modes keep their bias.
        if (exploreMode == PolicyParams.EXPLORE_DOOR_BIAS) {
            stepTowardNearestDoor(map, cell, knowledge)?.let {
                context?.clearExplorationTarget()
                return stepTo(it)
            }
        }

        // Committed goal: keep heading to the same target until reached/invalid. This is the key
        // anti-oscillation guard — without it, "nearest unvisited" flips between two directions at
        // the equidistant midpoint of a corridor and the agent turns around halfway.
        val committed = context?.explorationTargetKey?.let { parseKey(it) }
        if (committed != null && committed != cell && isTargetStillValid(committed, visited, knowledge, snapshot)) {
            val path = PolicyFpsPathfinder.path(map, cell, committed, allowVertical = true)
            if (path != null && path.size >= 2) {
                return stepTo(path[1])
            }
        }
        context?.clearExplorationTarget()

        // Choose a new directed goal: nearest reachable FRONTIER (boundary to the unknown). This
        // drives real map discovery and never targets the fully-seen room we just left.
        val frontierGoal = nearestReachableGoal(map, cell, knowledge.frontierCells(snapshot))
        if (frontierGoal != null) {
            context?.setExplorationTarget(cellKey(frontierGoal))
            val path = PolicyFpsPathfinder.path(map, cell, frontierGoal, allowVertical = true)
            if (path != null && path.size >= 2) return stepTo(path[1])
        }

        // No frontier reachable → stand on the nearest cell we have not stepped on yet
        // (covers seen-but-unvisited pockets that may hold pickups), committing to it.
        val unvisitedGoal = nearestReachableUnvisited(map, cell, visited)
        if (unvisitedGoal != null) {
            context?.setExplorationTarget(cellKey(unvisitedGoal))
            val path = PolicyFpsPathfinder.path(map, cell, unvisitedGoal, allowVertical = true)
            if (path != null && path.size >= 2) return stepTo(path[1])
            return stepTo(unvisitedGoal)
        }

        // Nothing new reachable → head for a known door / exit gate via a real path.
        stepTowardNearestDoor(map, cell, knowledge)?.let { return stepTo(it) }

        // Last resort: retreat to the last junction, else hold position (never random step).
        knowledge.lastJunctionCell(visitedTrail)?.let { junction ->
            val path = PolicyFpsPathfinder.path(map, cell, junction, allowVertical = true)
            if (path != null && path.size >= 2) return stepTo(path[1])
        }
        if (context != null) {
            return PolicyNavigation.anyMoveStep(sessionId, snapshot, context, map)
        }
        return PolicyFpsNavigation.syncStepToward(sessionId, pose, pose.x, pose.y, map)
    }

    /** A committed target is worth keeping while it is still a frontier or still un-stood-on. */
    private fun isTargetStillValid(
        target: GridPos,
        visited: Set<String>,
        knowledge: PlayerKnowledgeLayer,
        snapshot: GameSnapshot,
    ): Boolean {
        val key = cellKey(target)
        if (key !in visited) return true
        return knowledge.frontierCells(snapshot).any { cellKey(it) == key }
    }

    /** Nearest goal from [goals] reachable by a real FPS path (BFS by path length from player). */
    private fun nearestReachableGoal(
        map: ru.course.roguelike.shared.engine.TileMap,
        start: GridPos,
        goals: List<GridPos>,
    ): GridPos? {
        if (goals.isEmpty()) return null
        val goalSet = goals.toHashSet()
        val seen = hashSetOf(start)
        val queue = ArrayDeque<GridPos>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur != start && cur in goalSet) return cur
            for (n in PolicyFpsPathfinder.navigableNeighbors(map, cur, allowVertical = true)) {
                if (n in seen) continue
                seen.add(n)
                queue.addLast(n)
            }
        }
        return null
    }

    /** Nearest navigable cell not present in [visited] (BFS by path length from player). */
    private fun nearestReachableUnvisited(
        map: ru.course.roguelike.shared.engine.TileMap,
        start: GridPos,
        visited: Set<String>,
    ): GridPos? {
        val seen = hashSetOf(start)
        val queue = ArrayDeque<GridPos>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur != start && cellKey(cur) !in visited) return cur
            for (n in PolicyFpsPathfinder.navigableNeighbors(map, cur, allowVertical = true)) {
                if (n in seen) continue
                seen.add(n)
                queue.addLast(n)
            }
        }
        return null
    }

    private fun cellKey(cell: GridPos): String = PartialTileMap.cellKey(cell)

    private fun parseKey(key: String): GridPos? {
        val parts = key.split(",")
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return GridPos(x, y)
    }

    /** Next step toward the nearest navigable cell not present in [visited] (BFS shortest path). */
    internal fun stepTowardNearestUnvisited(
        map: ru.course.roguelike.shared.engine.TileMap,
        start: GridPos,
        visited: Set<String>,
    ): GridPos? {
        val parent = HashMap<GridPos, GridPos>()
        val seen = hashSetOf(start)
        val queue = ArrayDeque<GridPos>()
        queue.addLast(start)
        var goal: GridPos? = null
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur != start && PartialTileMap.cellKey(cur) !in visited) {
                goal = cur
                break
            }
            for (n in PolicyFpsPathfinder.navigableNeighbors(map, cur, allowVertical = true)) {
                if (n in seen) continue
                seen.add(n)
                parent[n] = cur
                queue.addLast(n)
            }
        }
        val g = goal ?: return null
        var cur = g
        while (parent[cur] != null && parent[cur] != start) {
            cur = parent[cur]!!
        }
        return cur
    }

    private fun stepTowardNearestDoor(
        map: ru.course.roguelike.shared.engine.TileMap,
        cell: GridPos,
        knowledge: PlayerKnowledgeLayer,
    ): GridPos? {
        val targets = buildList {
            knowledge.knownDoors.forEach { add(GridPos(it.x.toInt(), it.y.toInt())) }
            knowledge.knownExitGate?.let { add(it) }
        }.sortedBy { hypot((it.x - cell.x).toDouble(), (it.y - cell.y).toDouble()) }
        for (goal in targets) {
            val path = PolicyFpsPathfinder.path(map, cell, goal, allowVertical = true)
            if (path != null && path.size >= 2) return path[1]
        }
        return null
    }

    private fun openNeighborKeys(
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        strictFairPlay: Boolean = true,
    ): List<String> {
        val map = knowledge.navigableMap(snapshot, strictFairPlay)
        val cell = playerCell(snapshot, map)
        return listOf(
            GridPos(cell.x + 1, cell.y),
            GridPos(cell.x - 1, cell.y),
            GridPos(cell.x, cell.y + 1),
            GridPos(cell.x, cell.y - 1),
        ).filter { isWalkable(map, it) }
            .map { PartialTileMap.cellKey(it) }
    }

    private fun playerCell(snapshot: GameSnapshot, map: ru.course.roguelike.shared.engine.TileMap): GridPos {
        val pose = snapshot.player.pose
        return GridPos(kotlin.math.floor(pose.x).toInt(), kotlin.math.floor(pose.y).toInt())
    }

    private fun isWalkable(map: ru.course.roguelike.shared.engine.TileMap, cell: GridPos): Boolean {
        // Use capsule navigability, not raw tile.walkable: a column-pinched or lava cell is
        // "walkable" by tile flag but is a dead-end/hazard for FPS movement, which caused the
        // agent to treat wall indentations as corridors and ping-pong.
        return PolicyFpsPathfinder.isNavigable(map, cell)
    }
}
