package ru.course.roguelike.policy.planner

import ru.course.roguelike.agent.combat.AgentCombatHelper
import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.llm.AgentPromptBuilder
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.dsl.PolicyExploreHelper
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.knowledge.PartialTileMap
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Unified FPS-first navigation for policy-agent (no compass [game_act] moves except interact/wait).
 */
object PolicyNavigation {
    fun explore(
        sessionId: String,
        snapshot: GameSnapshot,
        context: PolicyContext,
        exploreMode: String = context.currentPolicy?.params?.exploreMode ?: PolicyParams.EXPLORE_UNVISITED,
    ): ToolCallDecision {
        val knowledge = context.knowledge
        val combatStyle = context.currentPolicy?.params?.combatStyle ?: PolicyParams.COMBAT_PLANT
        if (AgentCombatHelper.inActiveCombatRoom(snapshot)) {
            if (AgentCombatHelper.needsReload(snapshot)) {
                return PolicyCombatHelper.reloadDecision(sessionId, snapshot, combatStyle)
            }
            return PolicyCombatHelper.combatDecision(sessionId, snapshot, combatStyle)
        }
        if (AgentPromptBuilder.shouldInteractNow(snapshot)) {
            return AgentPromptBuilder.interactDecision(sessionId)
        }
        if (AgentDoorHelper.isNearAnyDoorSeal(snapshot) && !AgentDoorHelper.canPressE(snapshot)) {
            return PolicyDoorPlanner.plan(sessionId, snapshot, knowledge, context.strictFairPlay, context)
        }
        if (snapshot.keysCollected >= snapshot.keysRequired) {
            return PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, knowledge, targetHint = "exit", strictFairPlay = context.strictFairPlay,
                context = context,
            )
        }
        if (snapshot.keyPickups.isNotEmpty()) {
            return PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, knowledge, strictFairPlay = context.strictFairPlay, context = context,
            )
        }
        if (snapshot.keysCollected < snapshot.keysRequired) {
            val living = context.nearestLivingDoor(snapshot)
            if (living != null && PolicyDoorPlanner.hasPressableSeal(snapshot, living)) {
                return PolicyDoorPlanner.plan(sessionId, snapshot, knowledge, context.strictFairPlay, context)
            }
        }
        if (context.isTrapped()) {
            if (AgentDoorHelper.isNearAnyDoorSeal(snapshot) ||
                AgentDoorHelper.nearestVisibleDoor(snapshot) != null
            ) {
                return PolicyDoorPlanner.plan(sessionId, snapshot, knowledge, context.strictFairPlay, context)
            }
            return policyUnstuck(sessionId, snapshot, context)
        }
        return PolicyExploreHelper.moveTowardUnvisited(
            snapshot = snapshot,
            sessionId = sessionId,
            visited = context.visitedCells,
            knowledge = knowledge,
            stepIndex = context.stepIndex,
            lastBlockedMove = context.lastBlockedMove,
            avoidPosKey = context.previousPosKey,
            exploreMode = exploreMode,
            visitedTrail = context.visitedTrailList(),
            pingPong = context.isPingPong(),
            strictFairPlay = context.strictFairPlay,
            context = context,
        )
    }

    fun navigateDoor(sessionId: String, snapshot: GameSnapshot, context: PolicyContext): ToolCallDecision =
        PolicyDoorPlanner.plan(sessionId, snapshot, context.knowledge, context.strictFairPlay, context)

    fun fallbackStep(sessionId: String, snapshot: GameSnapshot, context: PolicyContext): ToolCallDecision =
        PolicyKeyHuntPlanner.plan(
            sessionId, snapshot, context.knowledge, strictFairPlay = context.strictFairPlay, context = context,
        )

    fun policyUnstuck(sessionId: String, snapshot: GameSnapshot, context: PolicyContext): ToolCallDecision {
        val unstuckMode = context.currentPolicy?.params?.unstuckMode ?: PolicyParams.UNSTUCK_DOOR
        return PolicyUnstuckPlanner.plan(
            sessionId = sessionId,
            snapshot = snapshot,
            knowledge = context.knowledge,
            stepIndex = context.stepIndex,
            lastBlockedMove = context.lastBlockedMove ?: context.lastAttemptedMove,
            avoidCellKey = context.previousPosKey,
            avoidTargetKeys = context.failedUnstuckTargets(),
            stuckAttempt = context.samePosStreak + context.stepIndex,
            unstuckMode = unstuckMode,
            visitedTrail = context.visitedTrailList(),
            pingPong = context.isPingPong(),
            strictFairPlay = context.strictFairPlay,
            visited = context.visitedCells,
        )
    }

    /**
     * Last-resort step when no planner found a distant goal. Never targets the current cell
     * (that degenerates to game_act WAIT and burns steps without moving).
     */
    fun anyMoveStep(
        sessionId: String,
        snapshot: GameSnapshot,
        context: PolicyContext,
        map: TileMap? = null,
    ): ToolCallDecision {
        val navMap = map ?: context.navigableMap(snapshot)
        val pose = snapshot.player.pose
        val cell = GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
        val failed = context.failedUnstuckTargets()
        val frontierKeys = context.knowledge.frontierCells(snapshot).map { PartialTileMap.cellKey(it) }.toSet()
        val neighbors = PolicyFpsPathfinder.navigableNeighbors(navMap, cell, allowVertical = true)
            .filter { it != cell && PartialTileMap.cellKey(it) !in failed }
        val next = neighbors.minWithOrNull(
            compareBy<GridPos>(
                { PartialTileMap.cellKey(it) !in frontierKeys },
                { PartialTileMap.cellKey(it) in context.visitedCells },
                { hypot((it.x - cell.x).toDouble(), (it.y - cell.y).toDouble()) },
            ),
        )
        if (next != null) {
            return PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, next, navMap)
        }
        val frontier = context.knowledge.frontierCells(snapshot)
            .minByOrNull { hypot((it.x - cell.x).toDouble(), (it.y - cell.y).toDouble()) }
        if (frontier != null) {
            val path = PolicyFpsPathfinder.path(navMap, cell, frontier, allowVertical = true)
            if (path != null && path.size >= 2) {
                return PolicyFpsNavigation.syncStepTowardCell(sessionId, pose, path[1], navMap)
            }
        }
        return PolicySyncActions.sync(sessionId, pose.yaw + 0.4f, forward = false, deltaMs = 80)
    }
}
