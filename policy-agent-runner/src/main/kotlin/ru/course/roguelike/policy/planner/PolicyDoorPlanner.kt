package ru.course.roguelike.policy.planner

import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.llm.AgentPromptBuilder
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * FPS door entry: approach floor beside D, aim at seal, interact (E).
 */
object PolicyDoorPlanner {
    /** True when a `ROOM_SEAL`/`ROOM_DOOR` tile with a matching marker exists near this door. */
    fun hasPressableSeal(snapshot: GameSnapshot, door: DoorMarkerSnapshot): Boolean {
        val map = AgentDoorHelper.tileMap(snapshot)
        return interactableSealCell(snapshot, map, door) != null
    }

    fun plan(
        sessionId: String,
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        strictFairPlay: Boolean = true,
        context: PolicyContext? = null,
        preferredDoorCell: GridPos? = null,
    ): ToolCallDecision {
        if (AgentDoorHelper.canPressE(snapshot)) {
            return AgentPromptBuilder.interactDecision(sessionId)
        }
        val door = preferredDoor(snapshot, knowledge, preferredDoorCell)
            ?: targetDoor(snapshot, knowledge)
            ?: return PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, knowledge, strictFairPlay = strictFairPlay, context = context,
            )
        // Door marker we already gave up on (no pressable seal): explore elsewhere instead of WAITing.
        if (context?.isDoorDead(AgentDoorHelper.doorCell(door)) == true) {
            return PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, knowledge, strictFairPlay = strictFairPlay, context = context,
            )
        }
        return approachDoor(sessionId, snapshot, knowledge, door, strictFairPlay, context)
    }

    fun enter(sessionId: String): ToolCallDecision = AgentPromptBuilder.interactDecision(sessionId)

    private fun approachDoor(
        sessionId: String,
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        door: DoorMarkerSnapshot,
        strictFairPlay: Boolean,
        context: PolicyContext? = null,
    ): ToolCallDecision {
        val pose = snapshot.player.pose
        val map = navigationMap(snapshot, knowledge, strictFairPlay)
        // Target the actual pressable seal tile, not the raw marker: a loot/weapon door marker can sit
        // on a cell that is NOT a ROOM_SEAL, so aiming at the marker never makes canPressE true.
        val dc = interactableSealCell(snapshot, map, door)
        if (dc == null) {
            context?.markDoorDead(door)
            return PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, knowledge, strictFairPlay = strictFairPlay, context = context,
            )
        }
        val sealX = dc.x + 0.5f
        val sealY = dc.y + 0.5f
        if (isAdjacentToCell(pose, dc) && !AgentDoorHelper.isFacingTarget(pose, sealX, sealY)) {
            return PolicyFpsNavigation.syncAimToward(sessionId, pose, sealX, sealY)
        }

        val cell = playerCell(snapshot, map)
        val approachGoals = listOf(
            GridPos(dc.x - 1, dc.y),
            GridPos(dc.x + 1, dc.y),
            GridPos(dc.x, dc.y - 1),
            GridPos(dc.x, dc.y + 1),
        ).filter { map.get(it)?.walkable == true }

        // Commit to a single approach cell for this door. Re-picking "nearest reachable" every step
        // flips between two equidistant approach cells (dc-1 vs dc+1) at a corridor midpoint and makes
        // the agent step in then back out. The commitment is reset when the objective/door changes.
        val committed = context?.committedApproachCell
            ?.takeIf { it in approachGoals && PolicyFpsPathfinder.path(map, cell, it, allowVertical = true) != null }
        val goal = committed ?: PolicyFpsPathfinder.nearestReachable(map, cell, approachGoals, allowVertical = true)
        if (goal != null) {
            context?.committedApproachCell = goal
            val path = PolicyFpsPathfinder.path(map, cell, goal, allowVertical = true)
            if (path != null && path.size >= 2) {
                return PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, path[1], map)
            }
            // Already on the approach tile but E is not ready — turn toward the seal, never blind interact.
            if (cell == goal) {
                return PolicyFpsNavigation.syncAimToward(sessionId, pose, sealX, sealY)
            }
        }

        val nearest = approachGoals.minByOrNull { hypot((it.x - cell.x).toDouble(), (it.y - cell.y).toDouble()) }
        return if (nearest != null) {
            PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, nearest, map)
        } else {
            PolicyFpsNavigation.syncAimToward(sessionId, pose, sealX, sealY)
        }
    }

    /**
     * Cell that the engine will actually accept for E: a `ROOM_SEAL`/`ROOM_DOOR` tile that also
     * carries a door marker (this is what [ru.course.roguelike.shared.engine.DoorInteraction] +
     * `canPressE` require). Returns null when the marker has no matching seal tile nearby (phantom /
     * loot-only door) so the caller can fall back instead of parking forever.
     */
    private fun interactableSealCell(
        snapshot: GameSnapshot,
        map: TileMap,
        door: DoorMarkerSnapshot,
    ): GridPos? {
        val dc = AgentDoorHelper.doorCell(door)
        if (isMarkedSeal(snapshot, map, dc)) return dc
        return (-SEAL_SEARCH..SEAL_SEARCH).flatMap { dx ->
            (-SEAL_SEARCH..SEAL_SEARCH).map { dy -> GridPos(dc.x + dx, dc.y + dy) }
        }.filter { isMarkedSeal(snapshot, map, it) }
            .minByOrNull { abs(it.x - dc.x) + abs(it.y - dc.y) }
    }

    private fun isMarkedSeal(snapshot: GameSnapshot, map: TileMap, cell: GridPos): Boolean {
        val tile = map.get(cell)
        if (tile != TileType.ROOM_SEAL && tile != TileType.ROOM_DOOR) return false
        return snapshot.doorMarkers.any {
            floor(it.x).toInt() == cell.x && floor(it.y).toInt() == cell.y
        }
    }

    private fun isAdjacentToCell(pose: ru.course.roguelike.shared.model.PlayerPose, cell: GridPos): Boolean {
        val px = floor(pose.x).toInt()
        val py = floor(pose.y).toInt()
        return abs(px - cell.x) + abs(py - cell.y) == 1
    }

    private const val SEAL_SEARCH = 2

    /** Door (visible or remembered) whose grid cell matches the LLM-chosen [preferredDoorCell]. */
    private fun preferredDoor(
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        preferredDoorCell: GridPos?,
    ): DoorMarkerSnapshot? {
        val want = preferredDoorCell ?: return null
        val candidates = snapshot.doorMarkers + knowledge.knownDoors
        return candidates.firstOrNull { AgentDoorHelper.doorCell(it) == want }
    }

    private fun navigationMap(
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        strictFairPlay: Boolean,
    ) = when {
        !strictFairPlay -> AgentDoorHelper.tileMap(snapshot)
        AgentDoorHelper.nearestVisibleDoor(snapshot) != null -> AgentDoorHelper.tileMap(snapshot)
        else -> knowledge.navigableMap(snapshot, strictFairPlay = true)
    }

    private fun targetDoor(snapshot: GameSnapshot, knowledge: PlayerKnowledgeLayer): DoorMarkerSnapshot? =
        AgentDoorHelper.nearestVisibleDoor(snapshot) ?: knowledge.nearestKnownDoor(snapshot)

    private fun playerCell(snapshot: GameSnapshot, map: ru.course.roguelike.shared.engine.TileMap): GridPos {
        val pose = snapshot.player.pose
        val primary = GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
        if (map.get(primary)?.walkable == true) return primary
        return primary
    }
}
