package ru.course.roguelike.policy.dsl

import ru.course.roguelike.agent.combat.AgentCombatHelper
import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.llm.AgentPromptBuilder
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.observation.PolicyObservation
import ru.course.roguelike.policy.dsl.PolicyExploreHelper
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.planner.PolicyCombatHelper
import ru.course.roguelike.policy.planner.PolicyDoorPlanner
import ru.course.roguelike.policy.planner.PolicyFpsPathfinder
import ru.course.roguelike.policy.planner.PolicyItemPlanner
import ru.course.roguelike.policy.planner.PolicyKeyHuntPlanner
import ru.course.roguelike.policy.planner.PolicyNavigation
import ru.course.roguelike.policy.planner.PolicyRoomExitPlanner
import ru.course.roguelike.policy.planner.PolicyUnstuckPlanner
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InteractionConstants
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Translates [AgentPolicy] + [GameSnapshot] into a single MCP tool call.
 * Planners execute chosen actions using fair-play [PolicyContext.knowledge].
 */
object PolicyInterpreter {
    fun interpret(
        policy: AgentPolicy,
        snapshot: GameSnapshot,
        sessionId: String,
        context: PolicyContext,
    ): InterpretResult {
        context.currentPolicy = policy
        context.lastObservation = PolicyObservation.observe(snapshot, context)
        context.releaseFalseRoomExitIfStuck(snapshot)
        context.updateDoorInteraction(snapshot)

        val matched = policy.rules.firstOrNull { rule ->
            rule.enabled && matches(rule.whenClause, snapshot, context, policy)
        }

        // 1) Reactive guards (combat/reload/room-exit/door-E/can-interact/inventory/stuck) always win,
        //    even over a committed objective — they are safety reflexes, not navigation choices.
        if (matched != null && matched.whenClause in GUARD_CONDITIONS) {
            val condition = matched.whenClause
            context.recordMatchedCondition(condition)
            return InterpretResult(
                decision = dispatch(matched, snapshot, sessionId, context, policy),
                matchedRule = matched,
                condition = condition,
            )
        }

        // 2) LLM-committed objective: honored until commitSteps elapses or target reached/invalid.
        //    Navigation fallback rules (frontier_available, explore, …) never override a valid objective.
        val objective = context.activeObjective
        if (objective != null && context.isObjectiveValid(snapshot)) {
            val condition = "objective:${objective.kind.lowercase()}"
            context.recordMatchedCondition(condition)
            return InterpretResult(
                decision = dispatchObjective(objective, snapshot, sessionId, context, policy),
                matchedRule = matched,
                condition = condition,
            )
        }

        // 3) Fallback: remaining navigation rules, then generic explore.
        if (matched != null) {
            val condition = matched.whenClause
            context.recordMatchedCondition(condition)
            return InterpretResult(
                decision = dispatch(matched, snapshot, sessionId, context, policy),
                matchedRule = matched,
                condition = condition,
            )
        }
        val fallback = fallbackDecision(sessionId, snapshot, context, policy)
        return InterpretResult(
            decision = fallback,
            matchedRule = null,
            condition = "fallback",
        )
    }

    /** No policy rule matched — still handle blocked doors and dead loot-door markers explicitly. */
    private fun fallbackDecision(
        sessionId: String,
        snapshot: GameSnapshot,
        context: PolicyContext,
        policy: AgentPolicy,
    ): ToolCallDecision {
        val nearBlockedDoor = AgentDoorHelper.isNearAnyDoorSeal(snapshot) &&
            !AgentDoorHelper.canPressE(snapshot)
        if (nearBlockedDoor) {
            if (context.isNearestDoorDead(snapshot)) {
                return PolicyExploreHelper.moveTowardUnvisited(
                    snapshot = snapshot,
                    sessionId = sessionId,
                    visited = context.visitedCells,
                    knowledge = context.knowledge,
                    stepIndex = context.stepIndex,
                    lastBlockedMove = context.lastBlockedMove,
                    avoidPosKey = context.previousPosKey,
                    exploreMode = PolicyParams.EXPLORE_FRONTIER,
                    visitedTrail = context.visitedTrailList(),
                    pingPong = context.isPingPong(),
                    strictFairPlay = context.strictFairPlay,
                    context = context,
                )
            }
            return PolicyDoorPlanner.plan(
                sessionId, snapshot, context.knowledge, context.strictFairPlay, context,
            )
        }
        return PolicyNavigation.explore(sessionId, snapshot, context, policy.params.exploreMode)
    }

    /** Conditions that override an LLM objective (reactive safety, not navigation goals). */
    private val GUARD_CONDITIONS: Set<String> = setOf(
        PolicyConditions.NEEDS_RELOAD,
        PolicyConditions.COMBAT_KITE,
        PolicyConditions.COMBAT_IN_ROOM,
        PolicyConditions.NEEDS_ROOM_EXIT,
        PolicyConditions.AT_DOOR_READY,
        PolicyConditions.AT_DOOR_NEED_ENTER,
        PolicyConditions.CAN_INTERACT,
        PolicyConditions.NEAR_VISIBLE_KEY,
        PolicyConditions.INVENTORY_FULL,
        PolicyConditions.NEEDS_WEAPON,
        PolicyConditions.CORNER_TRAPPED,
        PolicyConditions.STUCK,
    )

    private fun dispatchObjective(
        objective: PolicyObjective,
        snapshot: GameSnapshot,
        sessionId: String,
        context: PolicyContext,
        policy: AgentPolicy,
    ): ToolCallDecision {
        val target = context.objectiveTargetCell()
        return when (objective.kind.lowercase()) {
            ObjectiveKinds.ENTER_DOOR -> {
                context.committedDoorCell = target
                PolicyDoorPlanner.plan(
                    sessionId, snapshot, context.knowledge, context.strictFairPlay, context, target,
                )
            }
            ObjectiveKinds.REACH_KEY -> PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, context.knowledge, strictFairPlay = context.strictFairPlay, context = context,
            )
            ObjectiveKinds.REACH_EXIT -> PolicyKeyHuntPlanner.plan(
                sessionId, snapshot, context.knowledge, targetHint = "exit", strictFairPlay = context.strictFairPlay,
                context = context,
            )
            ObjectiveKinds.EXPLORE -> {
                target?.let { context.setExplorationTarget("${it.x},${it.y}") }
                PolicyExploreHelper.moveTowardUnvisited(
                    snapshot = snapshot,
                    sessionId = sessionId,
                    visited = context.visitedCells,
                    knowledge = context.knowledge,
                    stepIndex = context.stepIndex,
                    lastBlockedMove = context.lastBlockedMove,
                    avoidPosKey = context.previousPosKey,
                    exploreMode = policy.params.exploreMode,
                    visitedTrail = context.visitedTrailList(),
                    pingPong = context.isPingPong(),
                    strictFairPlay = context.strictFairPlay,
                    context = context,
                )
            }
            else -> PolicyNavigation.explore(sessionId, snapshot, context, policy.params.exploreMode)
        }
    }

    fun matches(
        whenClause: String,
        snapshot: GameSnapshot,
        context: PolicyContext,
        policy: AgentPolicy = context.currentPolicy ?: DefaultPolicies.standard(),
    ): Boolean = when (whenClause.lowercase()) {
        PolicyConditions.COMBAT_IN_ROOM ->
            AgentCombatHelper.inActiveCombatRoom(snapshot) && snapshot.mobs.isNotEmpty()
        PolicyConditions.NEEDS_ROOM_EXIT -> needsRoomExit(snapshot, context)
        PolicyConditions.COMBAT_KITE ->
            AgentCombatHelper.inActiveCombatRoom(snapshot) &&
                AgentCombatHelper.shouldEngage(snapshot) &&
                isCornerTrapped(snapshot, context)
        PolicyConditions.NEEDS_RELOAD ->
            AgentCombatHelper.inActiveCombatRoom(snapshot) && AgentCombatHelper.needsReload(snapshot)
        PolicyConditions.AT_DOOR_READY -> atDoorReady(snapshot, context)
        PolicyConditions.AT_DOOR_NEED_ENTER -> atDoorNeedEnter(snapshot, context)
        PolicyConditions.CAN_INTERACT -> AgentPromptBuilder.shouldInteractNow(snapshot)
        PolicyConditions.NEAR_VISIBLE_KEY -> nearVisibleKey(snapshot)
        PolicyConditions.INVENTORY_FULL ->
            PolicyInventoryHelper.isInventoryFull(snapshot) &&
                !AgentCombatHelper.inActiveCombatRoom(snapshot)
        PolicyConditions.NEEDS_WEAPON ->
            PolicyInventoryHelper.needsWeapon(snapshot) &&
                !AgentCombatHelper.inActiveCombatRoom(snapshot)
        PolicyConditions.HAS_VISIBLE_ITEM ->
            snapshot.items.isNotEmpty() &&
                snapshot.roomClearTimer == null &&
                !needsRoomExit(snapshot, context)
        PolicyConditions.HAS_VISIBLE_KEY -> snapshot.keyPickups.isNotEmpty()
        PolicyConditions.HAS_ALL_KEYS ->
            snapshot.keysCollected >= snapshot.keysRequired && !context.isTrapped()
        PolicyConditions.NEEDS_KEYS ->
            snapshot.keysCollected < snapshot.keysRequired &&
                !context.seekRoomExit &&
                !context.isInsideMobRoomForExit(snapshot) &&
                !AgentCombatHelper.inActiveCombatRoom(snapshot) &&
                !needsRoomExit(snapshot, context) &&
                !context.isTrapped() &&
                !atDoorNeedEnter(snapshot, context)
        PolicyConditions.CORNER_TRAPPED ->
            isCornerTrapped(snapshot, context) && !isDoorApproachLoop(snapshot, context)
        PolicyConditions.FRONTIER_AVAILABLE ->
            !needsRoomExit(snapshot, context) &&
                !AgentCombatHelper.inActiveCombatRoom(snapshot) &&
                PolicyExploreHelper.hasFrontier(context.knowledge, snapshot)
        PolicyConditions.HAS_UNVISITED_EXIT ->
            !needsRoomExit(snapshot, context) &&
                !AgentCombatHelper.inActiveCombatRoom(snapshot) &&
                !AgentPromptBuilder.shouldInteractNow(snapshot) &&
                !context.isTrapped() &&
                PolicyExploreHelper.hasUnvisitedExit(
                    snapshot, context.visitedCells, context.knowledge, context.strictFairPlay,
                )
        PolicyConditions.STUCK ->
            context.isTrapped() &&
                !context.seekRoomExit &&
                !context.mobRoomExitPending &&
                !isDoorApproachLoop(snapshot, context)
        PolicyConditions.EXPLORE ->
            !needsRoomExit(snapshot, context) &&
                !AgentCombatHelper.inActiveCombatRoom(snapshot) &&
                !context.isTrapped() &&
                !atDoorNeedEnter(snapshot, context)
        else -> whenClause.lowercase().startsWith("phase_is_") &&
            policy.phase?.equals(whenClause.removePrefix("phase_is_"), ignoreCase = true) == true
    }

    private fun isCornerTrapped(snapshot: GameSnapshot, context: PolicyContext): Boolean {
        val map = context.navigableMap(snapshot)
        val cell = GridPos(floor(snapshot.player.pose.x).toInt(), floor(snapshot.player.pose.y).toInt())
        return context.isTrapped() && PolicyFpsPathfinder.isCornerTrap(map, cell)
    }

    /** Ping-pong while approaching a door — unstuck retreat makes it worse (back 2 cells, re-approach). */
    private fun isDoorApproachLoop(snapshot: GameSnapshot, context: PolicyContext): Boolean {
        if (!context.isPingPong() && context.actionLoopStreak < 1) return false
        if (AgentDoorHelper.isNearAnyDoorSeal(snapshot)) return true
        return context.recentDoorApproachInTrace()
    }

    private fun atDoorReady(snapshot: GameSnapshot, context: PolicyContext): Boolean =
        AgentPromptBuilder.shouldInteractNow(snapshot) &&
            AgentDoorHelper.isNearAnyDoorSeal(snapshot) &&
            !needsRoomExit(snapshot, context) &&
            !AgentCombatHelper.inActiveCombatRoom(snapshot)

    private fun atDoorNeedEnter(snapshot: GameSnapshot, context: PolicyContext): Boolean =
        !AgentPromptBuilder.shouldInteractNow(snapshot) &&
            AgentDoorHelper.isNearAnyDoorSeal(snapshot) &&
            !context.isNearestDoorDead(snapshot) &&
            snapshot.roomClearTimer == null &&
            !needsRoomExit(snapshot, context) &&
            !AgentCombatHelper.inActiveCombatRoom(snapshot)

    private fun dispatch(
        rule: PolicyRule,
        snapshot: GameSnapshot,
        sessionId: String,
        context: PolicyContext,
        policy: AgentPolicy,
    ): ToolCallDecision = when (rule.action.lowercase()) {
        PolicyActions.COMBAT -> PolicyCombatHelper.combatDecision(sessionId, snapshot, policy.params.combatStyle)
        PolicyActions.COMBAT_KITE -> PolicyCombatHelper.repositionDecision(
            sessionId, snapshot, context.stepIndex, policy.params.combatStyle,
        )
        PolicyActions.LEAVE_ROOM, PolicyActions.EXIT_ROOM -> roomExitDecision(sessionId, snapshot, context)
        PolicyActions.RELOAD -> PolicyCombatHelper.reloadDecision(sessionId, snapshot, policy.params.combatStyle)
        PolicyActions.INTERACT, PolicyActions.ENTER_DOOR -> PolicyDoorPlanner.enter(sessionId)
        PolicyActions.APPROACH_DOOR -> PolicyDoorPlanner.plan(
            sessionId, snapshot, context.knowledge, context.strictFairPlay, context,
        )
        PolicyActions.NAVIGATE_KEY -> PolicyKeyHuntPlanner.plan(
            sessionId, snapshot, context.knowledge, rule.target, context.strictFairPlay, context,
        )
        PolicyActions.NAVIGATE_EXIT -> PolicyKeyHuntPlanner.plan(
            sessionId,
            snapshot,
            context.knowledge,
            rule.target ?: PolicyTargets.EXIT,
            context.strictFairPlay,
            context,
        )
        PolicyActions.NAVIGATE_DOOR -> PolicyNavigation.navigateDoor(sessionId, snapshot, context)
        PolicyActions.NAVIGATE_ITEM -> PolicyItemPlanner.navigateToItem(
            sessionId, snapshot, context.knowledge, context.strictFairPlay,
        )
        PolicyActions.EQUIP_WEAPON -> PolicyItemPlanner.equipWeapon(sessionId, snapshot)
        PolicyActions.MANAGE_INVENTORY -> PolicyItemPlanner.manageInventory(sessionId, snapshot)
        PolicyActions.UNSTUCK -> policyUnstuck(sessionId, snapshot, context, policy)
        PolicyActions.EXPLORE -> PolicyNavigation.explore(sessionId, snapshot, context, policy.params.exploreMode)
        PolicyActions.EXPLORE_UNVISITED -> if (
            context.isTrapped() ||
            context.knowledge.knownDoors.isNotEmpty() ||
            AgentDoorHelper.nearestVisibleDoor(snapshot) != null
        ) {
            if (context.isTrapped()) {
                policyUnstuck(sessionId, snapshot, context, policy)
            } else {
                PolicyNavigation.navigateDoor(sessionId, snapshot, context)
            }
        } else {
            PolicyExploreHelper.moveTowardUnvisited(
                snapshot = snapshot,
                sessionId = sessionId,
                visited = context.visitedCells,
                knowledge = context.knowledge,
                stepIndex = context.stepIndex,
                lastBlockedMove = context.lastBlockedMove,
                avoidPosKey = context.previousPosKey,
                exploreMode = policy.params.exploreMode,
                visitedTrail = context.visitedTrailList(),
                pingPong = context.isPingPong(),
                strictFairPlay = context.strictFairPlay,
                context = context,
            )
        }
        else -> PolicyNavigation.explore(sessionId, snapshot, context, policy.params.exploreMode)
    }

    private fun roomExitDecision(
        sessionId: String,
        snapshot: GameSnapshot,
        context: PolicyContext,
    ): ToolCallDecision {
        context.refreshFrozenExitGoals(snapshot)
        return PolicyRoomExitPlanner.navigateToExit(
            sessionId,
            snapshot,
            context.frozenRegionCellKeys(),
            context.frozenExitGoalCellKeys(),
            context.stepIndex,
            context.lastBlockedMove,
            avoidCellKey = context.previousPosKey,
            avoidTargetKeys = context.failedUnstuckTargets(),
            stuckAttempt = context.samePosStreak + context.roomExitStuckStreak + context.pingPongStreak,
            context = context,
        )
    }

    private fun needsRoomExit(snapshot: GameSnapshot, context: PolicyContext): Boolean {
        if (!context.seekRoomExit && !context.mobRoomExitPending) return false
        if (snapshot.roomClearTimer != null) return false
        if (AgentCombatHelper.inActiveCombatRoom(snapshot)) return false
        if (snapshot.keyPickups.isNotEmpty()) return false
        return context.isInsideMobRoomForExit(snapshot)
    }

    private fun policyUnstuck(
        sessionId: String,
        snapshot: GameSnapshot,
        context: PolicyContext,
        policy: AgentPolicy,
    ): ToolCallDecision {
        if (context.mobRoomExitPending || context.needsMobRoomExit(snapshot)) {
            return roomExitDecision(sessionId, snapshot, context)
        }
        if (atDoorNeedEnter(snapshot, context)) {
            return PolicyDoorPlanner.plan(
                sessionId, snapshot, context.knowledge, context.strictFairPlay, context,
            )
        }
        val avoidMove = context.lastBlockedMove ?: context.lastAttemptedMove
        val stuckAttempt = context.samePosStreak + context.stepIndex
        val map = context.navigableMap(snapshot)
        val cell = PolicyUnstuckPlanner.resolvePlayerCell(snapshot, map)
        val target = PolicyUnstuckPlanner.pickTargetCell(
            snapshot = snapshot,
            knowledge = context.knowledge,
            map = map,
            cell = cell,
            stepIndex = stuckAttempt,
            lastBlockedMove = avoidMove,
            avoidCellKey = context.previousPosKey,
            avoidTargetKeys = context.failedUnstuckTargets(),
            stuckAttempt = stuckAttempt,
            unstuckMode = policy.params.unstuckMode,
            visitedTrail = context.visitedTrailList(),
            pingPong = context.isPingPong(),
            visited = context.visitedCells,
        )
        if (target == cell) {
            return PolicyNavigation.anyMoveStep(sessionId, snapshot, context, map)
        }
        context.noteUnstuckTarget("${target.x},${target.y}")
        return ru.course.roguelike.policy.planner.PolicyFpsNavigation.syncStepTowardCell(
            sessionId,
            snapshot.player.pose,
            target,
            map,
        )
    }

    /** Key on floor is visible and close enough that pickup should preempt a committed objective. */
    private fun nearVisibleKey(snapshot: GameSnapshot): Boolean {
        if (snapshot.keyPickups.isEmpty()) return false
        val pose = snapshot.player.pose
        return snapshot.keyPickups.any {
            hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble()) <= KEY_PICKUP_GUARD_RADIUS
        }
    }

    private const val KEY_PICKUP_GUARD_RADIUS = 1.5
}

data class InterpretResult(
    val decision: ToolCallDecision,
    val matchedRule: PolicyRule?,
    val condition: String,
)
