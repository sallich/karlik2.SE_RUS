package ru.course.roguelike.policy.loop

import ru.course.roguelike.policy.planner.PolicyDoorPlanner
import ru.course.roguelike.policy.planner.PolicyRoomExitPlanner
import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.knowledge.PartialTileMap
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.protocol.GameActions
import kotlin.math.floor

/**
 * Accumulated run state for macro replanning (snapshots + progress signals + path memory).
 */
class PolicyContext(
    val stuckThreshold: Int = DEFAULT_STUCK_THRESHOLD,
    val replanEverySteps: Int = DEFAULT_REPLAN_INTERVAL,
    val stuckReplanCooldown: Int = DEFAULT_STUCK_REPLAN_COOLDOWN,
    val noProgressSteps: Int = DEFAULT_NO_PROGRESS_STEPS,
    val syncReplanCooldown: Int = DEFAULT_SYNC_REPLAN_COOLDOWN,
    val strictFairPlay: Boolean = true,
    val combatStallReplanCooldown: Int = DEFAULT_STUCK_REPLAN_COOLDOWN,
    val combatStallSteps: Int = DEFAULT_COMBAT_STALL_STEPS,
) {
    var stepIndex: Int = 0
    var samePosStreak: Int = 0
    var lastPosKey: String? = null
    var previousPosKey: String? = null
    var lastBlockedMove: String? = null
    /** Last compass action attempted (even if the player moved — breaks A↔B ping-pong). */
    var lastAttemptedMove: String? = null
    var lastPhase: String? = null
    var lastKeysCollected: Int = 0
    var seekRoomExit: Boolean = false
    /** Stays true from mob-room clear until player fully leaves enclosed room (survives seekRoomExit glitches). */
    internal var mobRoomExitPending: Boolean = false
    var lastHadRoomTimer: Boolean = false
    var lastActionError: Boolean = false
    var microStepsSinceReplan: Int = 0
    var replanCount: Int = 0
    var llmProvider: String = "unknown"
    var lastPolicySource: String = "none"
    /** Unique per run — decouples LLM sampling from labyrinth seed. */
    var runNonce: Long = 0L
        internal set
    var llmSampleSeed: Int = 0
        internal set
    var combatMobHpStallSteps: Int = 0
        internal set
    private var lastCombatMobHpSum: Int = -1
    var lastCombatStallReplanStep: Int = -1_000
        internal set
    var lastStuckReplanRequestStep: Int = -1_000
    var lastDoorStuckReplanStep: Int = -1_000
    var lastNoProgressReplanStep: Int = -1_000
    var lastSyncReplanStep: Int = -1_000
    var pendingReplanReason: ReplanReason? = null
    var stepsSinceProgress: Int = 0
    var pingPongStreak: Int = 0
    var roomExitStuckStreak: Int = 0
    var actionLoopStreak: Int = 0
    var lastMatchedCondition: String? = null
    var stuckEventCount: Int = 0

    /** Doors whose seal can never be pressed (marker present but no reachable ROOM_SEAL tile). */
    private val deadDoorKeys = mutableSetOf<String>()
    private var doorBlockedCellKey: String? = null
    private var doorBlockedStreak: Int = 0

    private var hadMobsInCurrentRoom: Boolean = false
    private var roomJustCleared: Boolean = false

    /** Frozen walkable cells of the mob room, captured on entry (before door unseals). */
    var frozenRoomRegion: Set<String>? = null
        private set

    /** Corridor seal cells for this mob room — exit targets once the door unseals. */
    var frozenExitGoalKeys: Set<String>? = null
        private set

    private var progressKeys: Int = 0
    private var progressPhase: String? = null

    val visitedCells = mutableSetOf<String>()
    val knowledge = PlayerKnowledgeLayer()
    private var stepsSinceKnowledgeGrowth: Int = 0
    private val visitedTrailKeys = ArrayDeque<String>(24)
    private val recentPosKeys = ArrayDeque<String>(6)
    private val actionTrace = ArrayDeque<ActionTraceEntry>()
    val snapshotTrail = mutableListOf<SnapshotEvent>()
    val replanLog = mutableListOf<String>()
    val macroDecisions = mutableListOf<MacroDecision>()
    var currentPolicy: AgentPolicy? = null

    var lastObservation: ru.course.roguelike.policy.observation.PolicyObservationResult? = null
        internal set

    var lastUnstuckTargetKey: String? = null
        internal set

    /** Committed exploration goal (frontier cell). Prevents mid-corridor flip-flop. */
    var explorationTargetKey: String? = null
        internal set

    /** LLM-chosen committed sub-goal; honored by the interpreter until reached/invalid/expired. */
    var activeObjective: PolicyObjective? = null
        private set
    private var objectiveStartStep: Int = 0
    private var lastObjectiveReplanStep: Int = -1_000

    /** Door cell + approach cell committed while pursuing an [ObjectiveKinds.ENTER_DOOR] objective. */
    var committedDoorCell: GridPos? = null
    var committedApproachCell: GridPos? = null

    /** Install a fresh objective (from a newly applied policy). Resets door/approach commitments. */
    fun setActiveObjective(objective: PolicyObjective?) {
        val changed = objective?.kind != activeObjective?.kind || objective?.target != activeObjective?.target
        if (changed) {
            committedDoorCell = null
            committedApproachCell = null
        }
        activeObjective = objective
        objectiveStartStep = stepIndex
    }

    fun objectiveTargetCell(): GridPos? = activeObjective?.target?.let { parseCellKey(it) }

    /** The committed objective is still worth executing (not expired and target still navigable). */
    fun isObjectiveValid(snapshot: GameSnapshot): Boolean {
        val obj = activeObjective ?: return false
        if (stepIndex - objectiveStartStep >= obj.commitSteps) return false
        val target = obj.target?.let { parseCellKey(it) }
        return if (target == null) {
            obj.kind !in ObjectiveKinds.TARGETED
        } else {
            isObjectiveTargetNavigable(obj.kind, target, snapshot) &&
                playerCellOf(snapshot) != target
        }
    }

    /** Fair-play: target from the LLM brief — known cell, frontier anchor, known door, or exit gate. */
    fun isObjectiveTargetNavigable(kind: String, target: GridPos, snapshot: GameSnapshot): Boolean {
        val key = PartialTileMap.cellKey(target)
        if (key in knowledge.knownCells) return true
        return when (kind.lowercase()) {
            ObjectiveKinds.EXPLORE ->
                knowledge.frontierCells(snapshot).any { it.x == target.x && it.y == target.y }
            ObjectiveKinds.ENTER_DOOR ->
                knowledge.knownDoors.any {
                    floor(it.x).toInt() == target.x && floor(it.y).toInt() == target.y
                }
            ObjectiveKinds.REACH_EXIT ->
                snapshot.exitGate?.let { it.x == target.x && it.y == target.y } == true
            ObjectiveKinds.REACH_KEY ->
                snapshot.keyPickups.any {
                    floor(it.x).toInt() == target.x && floor(it.y).toInt() == target.y
                }
            else -> false
        }
    }

    /** The LLM should pick the next goal: objective reached, invalidated, or its commit window elapsed. */
    fun objectiveNeedsReplan(snapshot: GameSnapshot): Boolean {
        val obj = activeObjective ?: return false
        if (stepIndex - objectiveStartStep >= obj.commitSteps) return true
        val target = obj.target?.let { parseCellKey(it) } ?: return false
        return !isObjectiveTargetNavigable(obj.kind, target, snapshot) ||
            playerCellOf(snapshot) == target
    }

    fun setExplorationTarget(key: String?) {
        explorationTargetKey = key
    }

    fun clearExplorationTarget() {
        explorationTargetKey = null
    }

    private val failedUnstuckTargetKeys = mutableSetOf<String>()

    fun failedUnstuckTargets(): Set<String> = failedUnstuckTargetKeys.toSet()

    fun noteUnstuckTarget(key: String) {
        lastUnstuckTargetKey = key
    }

    fun navigableMap(snapshot: GameSnapshot): TileMap =
        knowledge.navigableMap(snapshot, strictFairPlay)

    fun needsMobRoomExit(snapshot: GameSnapshot): Boolean =
        (seekRoomExit || mobRoomExitPending) &&
            isInsideMobRoomForExit(snapshot) &&
            snapshot.roomClearTimer == null

    fun recordSnapshot(snapshot: GameSnapshot, reason: String) {
        markVisited(snapshot)
        knowledge.update(snapshot, visitedCells)
        snapshotTrail.add(
            SnapshotEvent(
                step = stepIndex,
                phase = snapshot.phase,
                pos = posKey(snapshot),
                keys = "${snapshot.keysCollected}/${snapshot.keysRequired}",
                hp = snapshot.player.hp,
                roomTimer = snapshot.roomClearTimer?.remainingMs,
                reason = reason,
                visitedCount = visitedCells.size,
            ),
        )
        if (snapshotTrail.size > MAX_TRAIL) {
            snapshotTrail.removeAt(0)
        }
    }

    fun markVisited(snapshot: GameSnapshot) {
        val key = posKey(snapshot)
        visitedCells.add(key)
        visitedTrailKeys.addLast(key)
        while (visitedTrailKeys.size > MAX_VISITED_TRAIL) {
            visitedTrailKeys.removeFirst()
        }
    }

    fun visitedTrailList(): List<String> = visitedTrailKeys.toList()

    fun updateAfterStep(before: GameSnapshot, after: GameSnapshot, decision: ToolCallDecision, isError: Boolean) {
        stepIndex++
        microStepsSinceReplan++
        lastActionError = isError
        markVisited(after)

        val beforeKey = posKey(before)
        val afterKey = posKey(after)
        if (afterKey == beforeKey) {
            samePosStreak++
            if (samePosStreak >= stuckThreshold) {
                stuckEventCount++
            }
            if (decision.tool == "game_sync") {
                lastUnstuckTargetKey?.let { failedUnstuckTargetKeys.add(it) }
            } else {
                val action = decision.arguments["action"]?.toString()?.trim('"')
                if (action == GameActions.WAIT) {
                    lastUnstuckTargetKey?.let { failedUnstuckTargetKeys.add(it) }
                    lastUnstuckTargetKey = null
                    clearExplorationTarget()
                } else {
                    lastBlockedMove = action
                }
            }
        } else {
            samePosStreak = 0
            lastBlockedMove = null
            if (decision.tool == "game_sync") {
                if (seekRoomExit || mobRoomExitPending) {
                    // Anti-backtrack uses previousPosKey in room-exit planner; do not blacklist
                    // every successful step — that exhausts the only two cells in a column choke.
                } else {
                    failedUnstuckTargetKeys.clear()
                }
                lastUnstuckTargetKey = null
            }
            trackPositionChange(beforeKey, afterKey)
        }
        decision.arguments["action"]?.toString()?.let { lastAttemptedMove = it.trim('"') }
        previousPosKey = beforeKey
        lastPosKey = afterKey
        lastPhase = after.phase
        lastKeysCollected = after.keysCollected
        lastHadRoomTimer = after.roomClearTimer != null
        noteCombatProgress(after)

        if (after.roomClearTimer != null) {
            seekRoomExit = false
            ensureFrozenRoomCaptured(after)
        }
        if (before.roomClearTimer == null && after.roomClearTimer != null) {
            mobRoomExitPending = false
            frozenRoomRegion = null
            frozenExitGoalKeys = null
            ensureFrozenRoomCaptured(after)
            hadMobsInCurrentRoom = after.mobs.isNotEmpty() ||
                !frozenExitGoalKeys.isNullOrEmpty()
        }
        if (after.roomClearTimer != null && after.mobs.isNotEmpty()) {
            hadMobsInCurrentRoom = true
        }
        if (before.roomClearTimer != null && after.roomClearTimer == null) {
            if (frozenRoomRegion.isNullOrEmpty()) {
                ensureFrozenRoomCaptured(after)
            }
            val insideClearedMobRoom = !frozenRoomRegion.isNullOrEmpty() ||
                (hadMobsInCurrentRoom && PolicyRoomExitPlanner.isInsideEnclosedRoom(after))
            seekRoomExit = insideClearedMobRoom && !mobsRemainInFrozenRegion(after)
            roomJustCleared = seekRoomExit
            if (seekRoomExit) {
                mobRoomExitPending = true
            }
            hadMobsInCurrentRoom = false
        }
        if (seekRoomExit && hasLeftClearedRoom(after)) {
            seekRoomExit = false
            mobRoomExitPending = false
            frozenRoomRegion = null
            frozenExitGoalKeys = null
            roomExitStuckStreak = 0
            failedUnstuckTargetKeys.clear()
            lastUnstuckTargetKey = null
        } else if (mobRoomExitPending && !seekRoomExit &&
            after.roomClearTimer == null &&
            isInsideMobRoomForExit(after)
        ) {
            seekRoomExit = true
        }
        if (after.keysCollected != progressKeys || after.phase != progressPhase) {
            progressKeys = after.keysCollected
            progressPhase = after.phase
            stepsSinceProgress = 0
        } else {
            stepsSinceProgress++
        }
        if (knowledge.recordProgress()) {
            stepsSinceKnowledgeGrowth = 0
        } else {
            stepsSinceKnowledgeGrowth++
        }
    }

    fun recordMatchedCondition(condition: String) {
        if (condition == lastMatchedCondition && condition == "needs_room_exit") {
            roomExitStuckStreak++
        } else if (condition != "needs_room_exit") {
            roomExitStuckStreak = 0
        }
        lastMatchedCondition = condition
    }

    /** Micro-step trace for loop detection and sync replan prompts. */
    fun recordActionTrace(condition: String, snapshot: GameSnapshot) {
        actionTrace.addLast(ActionTraceEntry(stepIndex, condition, posKey(snapshot)))
        while (actionTrace.size > ACTION_TRACE_MAX) {
            actionTrace.removeFirst()
        }
        actionLoopStreak = if (detectBehaviorLoop()) actionLoopStreak + 1 else 0
    }

    fun actionTraceSummary(maxEntries: Int = 8): String =
        actionTrace.takeLast(maxEntries).joinToString("\n") { entry ->
            "  step=${entry.step} ${entry.condition} @ ${entry.pos}"
        }

    fun recentDoorApproachInTrace(within: Int = 4): Boolean =
        actionTrace.takeLast(within).any { entry ->
            entry.condition == "at_door_need_enter" ||
                entry.condition.startsWith("objective:enter_door")
        }

    /** True when the same step pattern keeps repeating and async replans did not break it. */
    fun needsLoopEscape(): Boolean =
        actionLoopStreak >= LOOP_ESCAPE_THRESHOLD &&
            stepIndex - lastSyncReplanStep >= syncReplanCooldown

    /** Reset navigation commitments after a blocking loop-escape replan. */
    fun onSyncReplanApplied(reason: ReplanReason) {
        if (reason != ReplanReason.LOOP_ESCAPE) return
        lastSyncReplanStep = stepIndex
        actionLoopStreak = 0
        pingPongStreak = 0
        samePosStreak = 0
        recentPosKeys.clear()
        roomExitStuckStreak = 0
        committedApproachCell = null
        committedDoorCell = null
        clearExplorationTarget()
        failedUnstuckTargetKeys.clear()
        lastUnstuckTargetKey = null
        actionTrace.clear()
    }

    private fun detectBehaviorLoop(): Boolean {
        if (isPingPong()) return true
        if (actionTrace.size >= 3) {
            val recent = actionTrace.takeLast(3)
            if (recent.all { it.condition == "fallback" || it.condition.contains("wait", ignoreCase = true) }) {
                return true
            }
        }
        if (actionTrace.size < 4) return false
        val recent = actionTrace.takeLast(5)
        val recentConditions = recent.map { it.condition }
        val recentPositions = recent.map { it.pos }

        // Steadily pursuing one LLM objective while moving is normal navigation — not a loop.
        if (recentConditions.all { it.startsWith("objective:") }) {
            return isPingPong() ||
                (recentPositions.distinct().size <= 2 && samePosStreak >= stuckThreshold)
        }

        if (recentConditions.size >= 4 && recentConditions.distinct().size <= 2) return true
        val doorOscillation = setOf(
            "at_door_need_enter",
            "stuck",
            "corner_trapped",
            "objective:enter_door",
        )
        val doorSteps = recentConditions.count { cond ->
            cond in doorOscillation || cond.startsWith("objective:enter")
        }
        return doorSteps >= 3 && isPingPong()
    }

    fun isRoomExitStuck(): Boolean = roomExitStuckStreak >= ROOM_EXIT_STUCK_THRESHOLD

    /**
     * Track a door we are parked at but cannot open. A `WEAPON_*`/loot door marker may not line up
     * with an actual `ROOM_SEAL` tile (or the seal is unreachable from here), so `canPressE` stays
     * false forever and the door planner degenerates to WAIT. After a few blocked steps we mark the
     * door cell dead so the interpreter stops forcing entry and explores elsewhere.
     */
    fun updateDoorInteraction(snapshot: GameSnapshot) {
        val door = AgentDoorHelper.nearestVisibleDoor(snapshot)
        val blocked = door != null &&
            AgentDoorHelper.isNearAnyDoorSeal(snapshot) &&
            !AgentDoorHelper.canPressE(snapshot)
        if (!blocked) {
            doorBlockedCellKey = null
            doorBlockedStreak = 0
            return
        }
        val key = doorCellKey(door!!)
        if (!PolicyDoorPlanner.hasPressableSeal(snapshot, door)) {
            deadDoorKeys.add(key)
            doorBlockedCellKey = null
            doorBlockedStreak = 0
            return
        }
        if (key == doorBlockedCellKey) doorBlockedStreak++ else {
            doorBlockedCellKey = key
            doorBlockedStreak = 1
        }
        if (doorBlockedStreak >= DOOR_BLOCKED_THRESHOLD) deadDoorKeys.add(key)
    }

    fun isDoorDead(cell: GridPos?): Boolean = cell != null && "${cell.x},${cell.y}" in deadDoorKeys

    /** True when the nearest visible door is one we already gave up on. */
    fun isNearestDoorDead(snapshot: GameSnapshot): Boolean {
        val door = AgentDoorHelper.nearestVisibleDoor(snapshot) ?: return false
        return doorCellKey(door) in deadDoorKeys
    }

    fun markDoorDead(door: ru.course.roguelike.shared.dto.DoorMarkerSnapshot) {
        deadDoorKeys.add(doorCellKey(door))
    }

    /** Nearest door marker that is not on the dead list (fair-play: visible or remembered). */
    fun nearestLivingDoor(snapshot: GameSnapshot): ru.course.roguelike.shared.dto.DoorMarkerSnapshot? {
        val seen = mutableSetOf<String>()
        val candidates = buildList {
            AgentDoorHelper.nearestVisibleDoor(snapshot)?.let { add(it) }
            knowledge.nearestKnownDoor(snapshot)?.let { add(it) }
            addAll(snapshot.doorMarkers)
            addAll(knowledge.knownDoors)
        }
        return candidates
            .filter { doorCellKey(it) !in seen && seen.add(doorCellKey(it)) }
            .filter { !isDoorDead(AgentDoorHelper.doorCell(it)) }
            .minByOrNull { AgentDoorHelper.distanceToDoor(snapshot.player.pose, it) }
    }

    private fun doorCellKey(door: ru.course.roguelike.shared.dto.DoorMarkerSnapshot): String {
        val c = AgentDoorHelper.doorCell(door)
        return "${c.x},${c.y}"
    }

    /**
     * Drop a forced room-exit that has no real exit. An open hall of COLUMNs / an ELEVATOR shaft can
     * be misread as an enclosed mob room; the planner then ping-pongs between fake column-gap exits.
     * Once we are stuck/oscillating and the planner cannot resolve any real exit, release the state so
     * normal navigation (LLM objective / explore toward the frontier or lift) takes over.
     */
    fun releaseFalseRoomExitIfStuck(snapshot: GameSnapshot) {
        if (!seekRoomExit && !mobRoomExitPending) return
        if (snapshot.roomClearTimer != null) return
        if (!isRoomExitStuck() && !isPingPong()) return
        // Sustained thrashing means the exit approach is broken even if a door is nominally reachable
        // (e.g. exit goals resolve to inside-the-room cells, or the path keeps re-blacklisting itself).
        // Past this hard cap, stop forcing exit unconditionally so the LLM/explore can re-route.
        val hardStuck = roomExitStuckStreak >= ROOM_EXIT_HARD_RELEASE
        if (!hardStuck && PolicyRoomExitPlanner.hasResolvableExit(snapshot, frozenExitGoalKeys)) return
        seekRoomExit = false
        mobRoomExitPending = false
        frozenRoomRegion = null
        frozenExitGoalKeys = null
        roomExitStuckStreak = 0
        failedUnstuckTargetKeys.clear()
        lastUnstuckTargetKey = null
    }

    /** Same cell stuck OR A↔B ping-pong (position changes but no progress). */
    fun isTrapped(): Boolean = samePosStreak >= stuckThreshold || isPingPong()

    fun isPingPong(): Boolean = pingPongStreak >= 1

    /** Player still inside cleared mob room or on its doorway — keep exit_room until fully out. */
    fun isInsideMobRoomForExit(snapshot: GameSnapshot): Boolean {
        val region = frozenRoomRegion
        if (region.isNullOrEmpty()) {
            return PolicyRoomExitPlanner.isInsideEnclosedRoom(snapshot)
        }
        val key = posKey(snapshot)
        if (key in region) return true
        if (PolicyRoomExitPlanner.isInsideEnclosedRoom(snapshot)) return true
        return isAdjacentToFrozenRegion(key, region)
    }

    private fun hasLeftClearedRoom(snapshot: GameSnapshot): Boolean {
        if (PolicyRoomExitPlanner.isInsideEnclosedRoom(snapshot)) return false
        return !isInsideMobRoomForExit(snapshot)
    }

    private fun isAdjacentToFrozenRegion(key: String, region: Set<String>): Boolean {
        val parts = key.split(",")
        if (parts.size != 2) return false
        val x = parts[0].toIntOrNull() ?: return false
        val y = parts[1].toIntOrNull() ?: return false
        return listOf(
            "${x + 1},$y",
            "${x - 1},$y",
            "$x,${y + 1}",
            "$x,${y - 1}",
        ).any { it in region }
    }

    private fun trackPositionChange(beforeKey: String, afterKey: String) {
        recentPosKeys.addLast(afterKey)
        while (recentPosKeys.size > PING_PONG_WINDOW) {
            recentPosKeys.removeFirst()
        }
        pingPongStreak = if (detectPingPongPattern()) pingPongStreak + 1 else 0
        if (pingPongStreak >= 1 && (seekRoomExit || mobRoomExitPending)) {
            failedUnstuckTargetKeys.clear()
        }
    }

    private fun detectPingPongPattern(): Boolean {
        if (recentPosKeys.size < 4) return false
        val r = recentPosKeys.takeLast(4)
        return r[3] == r[1] && r[2] == r[0] && r[3] != r[2]
    }

    fun isCombatStalled(): Boolean = combatMobHpStallSteps >= effectiveCombatStallSteps()

    fun effectiveCombatStallSteps(): Int {
        val risk = currentPolicy?.params?.riskLevel ?: PolicyParams.RISK_BALANCED
        return when (risk) {
            PolicyParams.RISK_AGGRESSIVE -> (combatStallSteps * 0.7).toInt().coerceAtLeast(1)
            PolicyParams.RISK_CAUTIOUS -> (combatStallSteps * 14 / 10)
            else -> combatStallSteps
        }
    }

    /** Periodic replan only after the LLM's commit window — not on a fixed tick mid-objective. */
    private fun shouldPeriodicReplan(snapshot: GameSnapshot): Boolean {
        val obj = activeObjective ?: return true
        val elapsed = stepIndex - objectiveStartStep
        if (elapsed < obj.commitSteps && isObjectiveValid(snapshot)) return false
        return true
    }

    fun recordMacroDecision(reason: ReplanReason, source: String, policy: AgentPolicy) {
        macroDecisions.add(MacroDecisionRecorder.record(stepIndex, reason, source, policy))
    }

    private fun noteCombatProgress(snapshot: GameSnapshot) {
        if (snapshot.roomClearTimer == null || snapshot.mobs.isEmpty()) {
            combatMobHpStallSteps = 0
            lastCombatMobHpSum = -1
            return
        }
        val hpSum = snapshot.mobs.sumOf { it.hp }
        if (lastCombatMobHpSum < 0) {
            lastCombatMobHpSum = hpSum
            combatMobHpStallSteps = 0
        } else if (hpSum == lastCombatMobHpSum) {
            combatMobHpStallSteps++
        } else {
            combatMobHpStallSteps = 0
            lastCombatMobHpSum = hpSum
        }
    }

    fun shouldReplan(snapshot: GameSnapshot, isInitial: Boolean): ReplanReason? {
        if (isInitial) return ReplanReason.INITIAL
        if (pendingReplanReason != null) return null
        return listOfNotNull(
            ReplanReason.ACTION_ERROR.takeIf { lastActionError },
            ReplanReason.LOOP_ESCAPE.takeIf { needsLoopEscape() },
            ReplanReason.DOOR_STUCK.takeIf {
                !seekRoomExit &&
                    !mobRoomExitPending &&
                    ru.course.roguelike.policy.observation.PolicyObservation.detectDoorStuck(snapshot, this) &&
                    stepIndex - lastDoorStuckReplanStep >= stuckReplanCooldown
            },
            ReplanReason.COMBAT_STALEMATE.takeIf {
                snapshot.roomClearTimer != null &&
                    snapshot.mobs.isNotEmpty() &&
                    isCombatStalled() &&
                    stepIndex - lastCombatStallReplanStep >= combatStallReplanCooldown
            },
            ReplanReason.STUCK.takeIf {
                (isRoomExitStuck() ||
                    (isTrapped() &&
                        !seekRoomExit &&
                        !mobRoomExitPending &&
                        !ru.course.roguelike.policy.observation.PolicyObservation.detectDoorStuck(snapshot, this))) &&
                    stepIndex - lastStuckReplanRequestStep >= stuckReplanCooldown
            },
            ReplanReason.NO_PROGRESS.takeIf {
                snapshot.roomClearTimer == null &&
                    !seekRoomExit &&
                    (stepsSinceProgress >= noProgressSteps || stepsSinceKnowledgeGrowth >= noProgressSteps) &&
                    stepIndex - lastNoProgressReplanStep >= noProgressSteps
            },
            ReplanReason.PHASE_CHANGE.takeIf {
                snapshot.phase != lastPhase && lastPhase != null
            },
            ReplanReason.KEY_COLLECTED.takeIf { snapshot.keysCollected > lastKeysCollected },
            ReplanReason.OBJECTIVE_DONE.takeIf {
                !seekRoomExit &&
                    !mobRoomExitPending &&
                    snapshot.roomClearTimer == null &&
                    objectiveNeedsReplan(snapshot) &&
                    stepIndex - lastObjectiveReplanStep >= stuckReplanCooldown
            },
            ReplanReason.ROOM_TIMER_CHANGE.takeIf { roomJustCleared },
            ReplanReason.INTERVAL.takeIf {
                !seekRoomExit &&
                    microStepsSinceReplan >= replanEverySteps &&
                    shouldPeriodicReplan(snapshot)
            },
        ).firstOrNull()
    }

    /** Loop escape blocks micro until LLM returns a fresh strategy (initial-policy quality). */
    fun isUrgentReplan(reason: ReplanReason): Boolean = reason == ReplanReason.LOOP_ESCAPE

    fun onReplanScheduled(reason: ReplanReason) {
        pendingReplanReason = reason
        if (reason == ReplanReason.ROOM_TIMER_CHANGE) {
            roomJustCleared = false
        }
        if (reason == ReplanReason.COMBAT_STALEMATE) {
            lastCombatStallReplanStep = stepIndex
            combatMobHpStallSteps = 0
        }
        if (reason == ReplanReason.STUCK || reason == ReplanReason.DOOR_STUCK) {
            lastStuckReplanRequestStep = stepIndex
        }
        if (reason == ReplanReason.DOOR_STUCK) {
            lastDoorStuckReplanStep = stepIndex
        }
        if (reason == ReplanReason.NO_PROGRESS) {
            lastNoProgressReplanStep = stepIndex
            stepsSinceProgress = 0
            stepsSinceKnowledgeGrowth = 0
            knowledge.resetProgressBaseline()
        }
        if (reason == ReplanReason.OBJECTIVE_DONE) {
            lastObjectiveReplanStep = stepIndex
            // Release the finished objective so the interpreter falls back to reactive rules until the
            // LLM commits the next goal — and so this trigger does not re-fire while the LLM thinks.
            activeObjective = null
        }
        replanLog.add("step=$stepIndex scheduled=${reason.name.lowercase()}")
    }

    fun markReplanned(reason: ReplanReason) {
        replanCount++
        microStepsSinceReplan = 0
        pendingReplanReason = null
        if (reason == ReplanReason.STUCK || reason == ReplanReason.DOOR_STUCK || reason == ReplanReason.LOOP_ESCAPE) {
            pingPongStreak = 0
            recentPosKeys.clear()
            roomExitStuckStreak = 0
        }
        replanLog.add("step=$stepIndex applied=${reason.name.lowercase()}")
    }

    fun initRunVariation(labyrinthSeed: Long, nonce: Long = System.nanoTime()) {
        runNonce = nonce xor System.currentTimeMillis()
        llmSampleSeed = (runNonce xor labyrinthSeed).toInt()
    }

    fun priorObjectiveTargets(): Set<String> =
        macroDecisions.mapNotNull { it.objectiveTarget }.toSet()

    fun seedFromSnapshot(snapshot: GameSnapshot) {
        lastPhase = snapshot.phase
        lastKeysCollected = snapshot.keysCollected
        lastHadRoomTimer = snapshot.roomClearTimer != null
        markVisited(snapshot)
        knowledge.update(snapshot, visitedCells)
        knowledge.resetProgressBaseline()
        lastPosKey = posKey(snapshot)
        progressKeys = snapshot.keysCollected
        progressPhase = snapshot.phase
    }

    fun visitedTrailSummary(maxCells: Int = 8): String =
        snapshotTrail.takeLast(maxCells).joinToString(" → ") { it.pos }

    fun frozenRegionCellKeys(): Set<String>? = frozenRoomRegion

    fun frozenExitGoalCellKeys(): Set<String>? = frozenExitGoalKeys

    fun refreshFrozenExitGoals(snapshot: GameSnapshot) {
        val region = frozenRoomRegion ?: return
        if (!frozenExitGoalKeys.isNullOrEmpty()) return
        frozenExitGoalKeys = PolicyRoomExitPlanner.captureExitGoalKeys(snapshot, region)
    }

    private fun mobsRemainInFrozenRegion(snapshot: GameSnapshot): Boolean {
        val region = frozenRoomRegion ?: return false
        if (snapshot.mobs.isEmpty()) return false
        return snapshot.mobs.any { mob ->
            val key = "${kotlin.math.floor(mob.x).toInt()},${kotlin.math.floor(mob.y).toInt()}"
            key in region
        }
    }

    private fun ensureFrozenRoomCaptured(snapshot: GameSnapshot) {
        if (!frozenRoomRegion.isNullOrEmpty()) return
        val region = PolicyRoomExitPlanner.captureRoomRegion(snapshot)
        if (region.isEmpty()) return
        frozenRoomRegion = region
        frozenExitGoalKeys = PolicyRoomExitPlanner.captureExitGoalKeys(snapshot, region)
    }

    private fun posKey(snapshot: GameSnapshot): String {
        val p = snapshot.player.pose
        return "${kotlin.math.floor(p.x).toInt()},${kotlin.math.floor(p.y).toInt()}"
    }

    private fun playerCellOf(snapshot: GameSnapshot): GridPos {
        val p = snapshot.player.pose
        return GridPos(floor(p.x).toInt(), floor(p.y).toInt())
    }

    private fun parseCellKey(key: String): GridPos? {
        val parts = key.split(",")
        if (parts.size != 2) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        return GridPos(x, y)
    }

    companion object {
        const val DEFAULT_STUCK_THRESHOLD = 3
        const val DEFAULT_REPLAN_INTERVAL = 40
        const val DEFAULT_STUCK_REPLAN_COOLDOWN = 15
        const val DEFAULT_NO_PROGRESS_STEPS = 25
        private const val MAX_TRAIL = 12
        private const val MAX_VISITED_TRAIL = 24
        private const val PING_PONG_WINDOW = 6
        private const val ACTION_TRACE_MAX = 12
        private const val LOOP_ESCAPE_THRESHOLD = 4
        private const val DEFAULT_SYNC_REPLAN_COOLDOWN = 15
        private const val ROOM_EXIT_STUCK_THRESHOLD = 3
        private const val ROOM_EXIT_HARD_RELEASE = 6
        private const val DOOR_BLOCKED_THRESHOLD = 3
        private const val DEFAULT_COMBAT_STALL_STEPS = 35
        private const val MAX_FAILED_TARGETS = 10
    }
}

enum class ReplanReason {
    INITIAL,
    LOOP_ESCAPE,
    COMBAT_STALEMATE,
    STUCK,
    DOOR_STUCK,
    NO_PROGRESS,
    PHASE_CHANGE,
    KEY_COLLECTED,
    OBJECTIVE_DONE,
    ROOM_TIMER_CHANGE,
    ACTION_ERROR,
    INTERVAL,
}

data class SnapshotEvent(
    val step: Int,
    val phase: String,
    val pos: String,
    val keys: String,
    val hp: Int,
    val roomTimer: Long?,
    val reason: String,
    val visitedCount: Int = 0,
)

data class ActionTraceEntry(
    val step: Int,
    val condition: String,
    val pos: String,
)

fun isTerminalPhase(phase: String): Boolean =
    phase == SessionPhase.LEVEL_COMPLETE.name || phase == SessionPhase.GAME_OVER.name
