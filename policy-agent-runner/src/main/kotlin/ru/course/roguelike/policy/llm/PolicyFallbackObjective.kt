package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.shared.dto.GameSnapshot

/**
 * Last-resort objective when the macro LLM is unavailable. Does not implement strategy — only picks
 * the first fair-play lead so planners have *some* navigation target until the next replan succeeds.
 */
object PolicyFallbackObjective {
    fun suggest(snapshot: GameSnapshot, knowledge: PlayerKnowledgeLayer): PolicyObjective? {
        knowledge.frontierCells(snapshot).firstOrNull()?.let { cell ->
            return PolicyObjective(
                kind = ObjectiveKinds.EXPLORE,
                target = "${cell.x},${cell.y}",
                commitSteps = ObjectiveKinds.DEFAULT_COMMIT_STEPS,
            )
        }
        knowledge.nearestKnownDoor(snapshot)?.let { door ->
            return PolicyObjective(
                kind = ObjectiveKinds.ENTER_DOOR,
                target = "${door.x.toInt()},${door.y.toInt()}",
                commitSteps = 25,
            )
        }
        snapshot.exitGate?.takeIf { snapshot.keysCollected >= snapshot.keysRequired }?.let { exit ->
            return PolicyObjective(
                kind = ObjectiveKinds.REACH_EXIT,
                target = "${exit.x},${exit.y}",
                commitSteps = 30,
            )
        }
        return PolicyObjective(
            kind = ObjectiveKinds.EXPLORE,
            target = null,
            commitSteps = ObjectiveKinds.DEFAULT_COMMIT_STEPS,
        )
    }
}
