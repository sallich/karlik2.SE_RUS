package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.observation.PolicyObservation
import ru.course.roguelike.shared.dto.GameSnapshot

/** Compact fair-play brief for macro (LLM) prompts. */
object PolicySnapshotBrief {
    fun situation(snapshot: GameSnapshot, context: PolicyContext): String {
        val observation = context.lastObservation ?: PolicyObservation.observe(snapshot, context)
        val knowledgeBrief = context.knowledge.toBrief(snapshot)
        return """
            ${PolicyObservation.formatBrief(observation)}
            keys=${snapshot.keysCollected}/${snapshot.keysRequired} phase=${snapshot.phase}
            visited=${context.visitedCells.size} samePos=${context.samePosStreak}
            lastBlocked=${context.lastBlockedMove ?: "none"}
            $knowledgeBrief
        """.trimIndent()
    }

    /** Factual replan context only — no prescriptive recipes; the LLM decides what to change. */
    fun replanContext(reason: String, snapshot: GameSnapshot, context: PolicyContext): String {
        val observation = context.lastObservation ?: PolicyObservation.observe(snapshot, context)
        val signals = observation.failureSignals.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name } ?: "none"
        val nearestDoor = context.knowledge.nearestKnownDoor(snapshot)?.let { door ->
            "nearestKnownDoor=(${door.x.toInt()},${door.y.toInt()}) prizeIsKey=${door.prizeIsKey}"
        } ?: "nearestKnownDoor=none"
        return buildList {
            add("trigger=$reason")
            add("situation=${observation.situation.name}")
            add("failureSignals=$signals")
            add("keys=${snapshot.keysCollected}/${snapshot.keysRequired}")
            add("seekRoomExit=${context.seekRoomExit}")
            add("mobRoomExitPending=${context.mobRoomExitPending}")
            add("roomTimer=${snapshot.roomClearTimer?.remainingMs ?: "none"}")
            add("combatStallSteps=${context.combatMobHpStallSteps}")
            add(nearestDoor)
            add("visitedCells=${context.visitedCells.size}")
        }.joinToString("; ")
    }
}
