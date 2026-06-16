package ru.course.roguelike.policy.observation



import ru.course.roguelike.agent.combat.AgentCombatHelper

import ru.course.roguelike.agent.explore.AgentDoorHelper

import ru.course.roguelike.agent.llm.AgentPromptBuilder

import ru.course.roguelike.agent.map.MapCellRenderer

import ru.course.roguelike.policy.dsl.PolicyInventoryHelper

import ru.course.roguelike.policy.loop.PolicyContext

import ru.course.roguelike.policy.planner.PolicyFpsPathfinder

import ru.course.roguelike.shared.dto.GameSnapshot

import ru.course.roguelike.shared.model.GridPos

import kotlin.math.floor



enum class PolicySituation {

    COMBAT,

    LEAVE_ROOM,

    AT_DOOR_READY,

    AT_DOOR_BLOCKED,

    CORRIDOR,

    LOOT_VISIBLE,

    HAS_ALL_KEYS,

    NEEDS_KEYS,

    STUCK,

}



enum class FailureSignal {

    DOOR_E_NOT_READY,

    PING_PONG,

    SYNC_NO_PROGRESS,

    ROOM_EXIT_STUCK,

    INVENTORY_FULL,

    NO_WEAPON,

    ON_LAVA,

}



data class PolicyObservationResult(

    val situation: PolicySituation,

    val failureSignals: Set<FailureSignal>,

    val doorHint: String,

    val inventorySummary: String,

    val itemsNearby: String,

    val sessionFlags: String,

)



object PolicyObservation {

    fun observe(snapshot: GameSnapshot, context: PolicyContext): PolicyObservationResult {

        context.knowledge.update(snapshot, context.visitedCells)

        val signals = mutableSetOf<FailureSignal>()

        if (context.isPingPong()) signals.add(FailureSignal.PING_PONG)

        if (context.samePosStreak >= 2 && context.lastUnstuckTargetKey != null) {

            signals.add(FailureSignal.SYNC_NO_PROGRESS)

        }

        if (context.isRoomExitStuck()) signals.add(FailureSignal.ROOM_EXIT_STUCK)

        if (PolicyInventoryHelper.isInventoryFull(snapshot)) signals.add(FailureSignal.INVENTORY_FULL)

        if (PolicyInventoryHelper.needsWeapon(snapshot)) signals.add(FailureSignal.NO_WEAPON)



        val atDoor = AgentDoorHelper.isNearAnyDoorSeal(snapshot)

        val canE = AgentPromptBuilder.shouldInteractNow(snapshot)

        if (atDoor && !canE) signals.add(FailureSignal.DOOR_E_NOT_READY)

        val px = floor(snapshot.player.pose.x).toInt()
        val py = floor(snapshot.player.pose.y).toInt()
        if (MapCellRenderer.isLavaAt(snapshot, px, py)) {
            signals.add(FailureSignal.ON_LAVA)
        }



        val situation = classify(snapshot, context, atDoor, canE)

        val door = AgentDoorHelper.nearestVisibleDoor(snapshot) ?: context.knowledge.nearestKnownDoor(snapshot)

        val doorHint = when {

            canE -> "can_press_E=true (${AgentDoorHelper.doorStuckHint(snapshot) ?: "interact now"})"

            atDoor && door != null -> {

                val facing = AgentDoorHelper.isFacingTarget(snapshot.player.pose, door.x, door.y)

                "near_D=(${door.x.toInt()},${door.y.toInt()}) facing=$facing adjacent=" +

                    AgentDoorHelper.isAdjacentToDoorCell(snapshot.player.pose, door)

            }

            else -> "knownDoors=${context.knowledge.formatKnownDoors(3)}"

        }



        return PolicyObservationResult(

            situation = situation,

            failureSignals = signals,

            doorHint = doorHint,

            inventorySummary = PolicyInventoryHelper.summary(snapshot),

            itemsNearby = formatItems(snapshot),

            sessionFlags = "hp=${snapshot.player.hp}/${snapshot.player.maxHp} " +
                "seekRoomExit=${context.seekRoomExit} mobRoomExitPending=${context.mobRoomExitPending} " +
                "knownCells=${context.knowledge.knownCells.size} stuck=${context.samePosStreak} " +
                MapCellRenderer.hazardsNear(snapshot, px, py),

        )

    }



    fun detectDoorStuck(snapshot: GameSnapshot, context: PolicyContext): Boolean =

        context.isTrapped() &&

            !context.seekRoomExit &&

            !context.mobRoomExitPending &&

            snapshot.roomClearTimer == null &&

            (AgentDoorHelper.isNearAnyDoorSeal(snapshot) ||

                AgentDoorHelper.hasDoorSealInView(snapshot))



    fun isCornerTrapped(snapshot: GameSnapshot, context: PolicyContext): Boolean {

        val map = context.navigableMap(snapshot)

        val cell = GridPos(floor(snapshot.player.pose.x).toInt(), floor(snapshot.player.pose.y).toInt())

        return context.isTrapped() && PolicyFpsPathfinder.isCornerTrap(map, cell)

    }



    fun formatBrief(result: PolicyObservationResult): String = buildString {

        appendLine("situation=${result.situation.name.lowercase()}")

        if (result.failureSignals.isNotEmpty()) {

            appendLine("failureSignals=${result.failureSignals.joinToString { it.name }}")

        }

        appendLine(result.doorHint)

        appendLine("inventory: ${result.inventorySummary}")

        appendLine("items: ${result.itemsNearby}")

        appendLine("session: ${result.sessionFlags}")

    }



    private fun classify(

        snapshot: GameSnapshot,

        context: PolicyContext,

        atDoor: Boolean,

        canE: Boolean,

    ): PolicySituation = when {

        AgentCombatHelper.inActiveCombatRoom(snapshot) && snapshot.mobs.isNotEmpty() -> PolicySituation.COMBAT

        context.needsMobRoomExit(snapshot) -> PolicySituation.LEAVE_ROOM

        canE && atDoor -> PolicySituation.AT_DOOR_READY

        atDoor -> PolicySituation.AT_DOOR_BLOCKED

        context.isTrapped() -> PolicySituation.STUCK

        snapshot.items.isNotEmpty() && snapshot.roomClearTimer == null -> PolicySituation.LOOT_VISIBLE

        snapshot.keysCollected >= snapshot.keysRequired -> PolicySituation.HAS_ALL_KEYS

        snapshot.keysCollected < snapshot.keysRequired -> PolicySituation.NEEDS_KEYS

        else -> PolicySituation.CORRIDOR

    }



    private fun formatItems(snapshot: GameSnapshot): String =

        if (snapshot.items.isEmpty()) {

            "none"

        } else {

            snapshot.items.take(4).joinToString { "${it.kind.name}@(${it.x.toInt()},${it.y.toInt()})" }

        }

}

