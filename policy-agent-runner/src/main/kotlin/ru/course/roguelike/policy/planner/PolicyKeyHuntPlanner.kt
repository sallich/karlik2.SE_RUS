package ru.course.roguelike.policy.planner

import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.llm.AgentPromptBuilder
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.knowledge.PartialTileMap
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.policy.dsl.PolicyExploreHelper
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.planner.PolicyNavigation
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InteractionConstants
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

object PolicyKeyHuntPlanner {
    fun plan(
        sessionId: String,
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        targetHint: String? = null,
        strictFairPlay: Boolean = true,
        context: PolicyContext? = null,
    ): ToolCallDecision {
        if (shouldPickUpKey(snapshot)) {
            return AgentPromptBuilder.interactDecision(sessionId)
        }
        val map = navigationMap(snapshot, knowledge, strictFairPlay)
        val cell = playerCell(snapshot, map)
        nearestKey(snapshot)?.let { key ->
            val dist = hypot(
                (key.x - snapshot.player.pose.x).toDouble(),
                (key.y - snapshot.player.pose.y).toDouble(),
            )
            if (dist > InteractionConstants.INTERACT_RADIUS && dist <= KEY_APPROACH_RADIUS) {
                return PolicyFpsNavigation.syncStepToward(
                    sessionId = sessionId,
                    pose = snapshot.player.pose,
                    targetX = key.x,
                    targetY = key.y,
                    map = map,
                )
            }
        }
        val goal = pickGoal(snapshot, knowledge, cell, targetHint, strictFairPlay, map, context)
            ?: return exploreAway(sessionId, snapshot, knowledge, context, strictFairPlay)
        val path = PolicyFpsPathfinder.path(map, cell, goal, allowVertical = true)
        if (cell == goal) {
            if (keyOnCell(snapshot, goal)) {
                return AgentPromptBuilder.interactDecision(sessionId)
            }
            if (AgentPromptBuilder.shouldInteractNow(snapshot)) {
                return AgentPromptBuilder.interactDecision(sessionId)
            }
            if (AgentDoorHelper.isNearAnyDoorSeal(snapshot) && !AgentDoorHelper.canPressE(snapshot)) {
                return PolicyDoorPlanner.plan(sessionId, snapshot, knowledge, strictFairPlay, context)
            }
            if (context != null) {
                return PolicyNavigation.anyMoveStep(sessionId, snapshot, context, map)
            }
            return PolicyFpsNavigation.syncStepToward(sessionId, snapshot.player.pose, cell.x + 0.5f, cell.y + 0.5f, map)
        }
        val next = when {
            path != null && path.size >= 2 -> path[1]
            else -> goal
        }
        return PolicyFpsNavigation.syncStepTowardCell(sessionId, snapshot.player.pose, next, map)
    }

    private fun exploreAway(
        sessionId: String,
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        context: PolicyContext?,
        strictFairPlay: Boolean,
    ): ToolCallDecision {
        if (context != null) {
            return PolicyExploreHelper.moveTowardUnvisited(
                snapshot = snapshot,
                sessionId = sessionId,
                visited = context.visitedCells,
                knowledge = knowledge,
                stepIndex = context.stepIndex,
                lastBlockedMove = context.lastBlockedMove,
                avoidPosKey = context.previousPosKey,
                exploreMode = PolicyParams.EXPLORE_FRONTIER,
                visitedTrail = context.visitedTrailList(),
                pingPong = context.isPingPong(),
                strictFairPlay = strictFairPlay,
                context = context,
            )
        }
        val map = AgentDoorHelper.tileMap(snapshot)
        val cell = playerCell(snapshot, map)
        val neighbor = PolicyFpsPathfinder.navigableNeighbors(map, cell, allowVertical = true)
            .firstOrNull { it != cell }
        if (neighbor != null) {
            return PolicyFpsNavigation.syncStepTowardCell(sessionId, snapshot.player.pose, neighbor, map)
        }
        return PolicySyncActions.sync(sessionId, snapshot.player.pose.yaw + 0.4f, forward = false, deltaMs = 80)
    }

    private fun pickGoal(
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        cell: GridPos,
        targetHint: String?,
        strictFairPlay: Boolean,
        map: ru.course.roguelike.shared.engine.TileMap,
        context: PolicyContext?,
    ): GridPos? {
        if (targetHint == "exit" || targetHint == "nearest_known") {
            knowledge.knownExitGate?.let { gate ->
                PolicyFpsPathfinder.nearestReachable(map, cell, listOf(gate), allowVertical = true)?.let { return it }
            }
        }
        val keyGoals = snapshot.keyPickups.map {
            GridPos(floor(it.x).toInt(), floor(it.y).toInt())
        }.filter { !strictFairPlay || PartialTileMap.cellKey(it) in knowledge.knownCells }
        val keyPriority = context?.currentPolicy?.params?.keyPriority ?: ru.course.roguelike.policy.dsl.PolicyParams.KEYS_FIRST
        val doorGoal = if (snapshot.keysCollected < snapshot.keysRequired) {
            resolveDoorGoal(snapshot, knowledge, cell, map, context)
        } else {
            null
        }
        if (keyPriority == ru.course.roguelike.policy.dsl.PolicyParams.DOORS_FIRST) {
            doorGoal?.let { return it }
            if (keyGoals.isNotEmpty()) {
                PolicyFpsPathfinder.nearestReachable(map, cell, keyGoals, allowVertical = true)?.let { return it }
            }
        } else {
            if (keyGoals.isNotEmpty()) {
                PolicyFpsPathfinder.nearestReachable(map, cell, keyGoals, allowVertical = true)?.let { return it }
            }
            doorGoal?.let { return it }
        }
        knowledge.knownExitGate?.let { gate ->
            PolicyFpsPathfinder.nearestReachable(map, cell, listOf(gate), allowVertical = true)?.let { return it }
        }
        snapshot.exitGate?.takeIf {
            !strictFairPlay || PartialTileMap.cellKey(it.x, it.y) in knowledge.knownCells
        }?.let { gate ->
            PolicyFpsPathfinder.nearestReachable(map, cell, listOf(gate), allowVertical = true)?.let { return it }
        }
        return null
    }

    private fun resolveDoorGoal(
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        cell: GridPos,
        map: ru.course.roguelike.shared.engine.TileMap,
        context: PolicyContext?,
    ): GridPos? {
        val door = context?.nearestLivingDoor(snapshot)
            ?: AgentDoorHelper.nearestVisibleDoor(snapshot)?.takeUnless {
                context?.isDoorDead(AgentDoorHelper.doorCell(it)) == true
            }
            ?: knowledge.nearestKnownDoor(snapshot)?.takeUnless {
                context?.isDoorDead(AgentDoorHelper.doorCell(it)) == true
            }
        if (door == null || !PolicyDoorPlanner.hasPressableSeal(snapshot, door)) return null
        val dc = AgentDoorHelper.doorCell(door)
        val approach = listOf(
            GridPos(dc.x - 1, dc.y),
            GridPos(dc.x + 1, dc.y),
            GridPos(dc.x, dc.y - 1),
            GridPos(dc.x, dc.y + 1),
        ).filter { map.get(it)?.walkable == true }
        return PolicyFpsPathfinder.nearestReachable(map, cell, approach, allowVertical = true)
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

    private fun playerCell(snapshot: GameSnapshot, map: ru.course.roguelike.shared.engine.TileMap): GridPos {
        val pose = snapshot.player.pose
        val primary = GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
        if (map.get(primary) == TileType.FLOOR || map.get(primary) == TileType.EXIT_GATE) return primary
        return primary
    }

    private fun shouldPickUpKey(snapshot: GameSnapshot): Boolean {
        if (AgentPromptBuilder.shouldInteractNow(snapshot)) return true
        val pose = snapshot.player.pose
        return snapshot.keyPickups.any {
            hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble()) <= InteractionConstants.INTERACT_RADIUS
        }
    }

    private fun nearestKey(snapshot: GameSnapshot) =
        snapshot.keyPickups.minByOrNull {
            hypot(
                (it.x - snapshot.player.pose.x).toDouble(),
                (it.y - snapshot.player.pose.y).toDouble(),
            )
        }

    private fun keyOnCell(snapshot: GameSnapshot, cell: GridPos): Boolean =
        snapshot.keyPickups.any {
            floor(it.x).toInt() == cell.x && floor(it.y).toInt() == cell.y
        }

    /** Cardinal-adjacent reach toward a key before [InteractionConstants.INTERACT_RADIUS]. */
    private const val KEY_APPROACH_RADIUS = 1.5f
}
