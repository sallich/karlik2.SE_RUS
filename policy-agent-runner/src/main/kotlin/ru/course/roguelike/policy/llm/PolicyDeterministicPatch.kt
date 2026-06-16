package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.PolicyActions
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.dsl.PolicyRule
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.loop.ReplanReason
import ru.course.roguelike.policy.observation.FailureSignal
import ru.course.roguelike.policy.observation.PolicyObservationResult
import ru.course.roguelike.shared.dto.GameSnapshot

/**
 * Minimal macro fallback when Ollama replan fails. Keeps the LLM's strategic choices (objective,
 * params, phase) and only toggles execution/reflex rules so the agent can survive until the next
 * successful LLM call.
 */
object PolicyDeterministicPatch {
    fun apply(
        current: AgentPolicy,
        observation: PolicyObservationResult,
        reason: ReplanReason,
        context: PolicyContext,
        snapshot: GameSnapshot,
    ): AgentPolicy {
        val rules = current.rules.associateBy { it.whenClause.lowercase() }.toMutableMap()

        fun enable(clause: String, action: String) {
            val base = rules[clause] ?: PolicyRule(clause, action)
            rules[clause] = base.copy(enabled = true, action = action)
        }
        fun disable(clause: String) {
            rules[clause]?.let { rules[clause] = it.copy(enabled = false) }
        }

        when {
            reason == ReplanReason.LOOP_ESCAPE -> {
                disable(PolicyConditions.STUCK)
                disable(PolicyConditions.CORNER_TRAPPED)
                enable(PolicyConditions.FRONTIER_AVAILABLE, PolicyActions.EXPLORE_UNVISITED)
            }
            reason == ReplanReason.DOOR_STUCK ||
                FailureSignal.DOOR_E_NOT_READY in observation.failureSignals -> {
                enable(PolicyConditions.AT_DOOR_READY, PolicyActions.ENTER_DOOR)
                enable(PolicyConditions.AT_DOOR_NEED_ENTER, PolicyActions.APPROACH_DOOR)
                enable(PolicyConditions.CAN_INTERACT, PolicyActions.INTERACT)
            }
            reason == ReplanReason.STUCK || observation.situation.name == "STUCK" ||
                FailureSignal.ROOM_EXIT_STUCK in observation.failureSignals ||
                observation.situation.name == "LEAVE_ROOM" &&
                FailureSignal.PING_PONG in observation.failureSignals -> {
                enable(PolicyConditions.STUCK, PolicyActions.UNSTUCK)
                if (FailureSignal.ROOM_EXIT_STUCK in observation.failureSignals ||
                    observation.situation.name == "LEAVE_ROOM"
                ) {
                    enable(PolicyConditions.NEEDS_ROOM_EXIT, PolicyActions.EXIT_ROOM)
                }
                if (observation.situation.name == "AT_DOOR_BLOCKED") {
                    enable(PolicyConditions.AT_DOOR_NEED_ENTER, PolicyActions.APPROACH_DOOR)
                }
                if (FailureSignal.PING_PONG in observation.failureSignals) {
                    enable(PolicyConditions.CORNER_TRAPPED, PolicyActions.UNSTUCK)
                }
            }
            reason == ReplanReason.ROOM_TIMER_CHANGE -> {
                enable(PolicyConditions.NEEDS_ROOM_EXIT, PolicyActions.EXIT_ROOM)
            }
            reason == ReplanReason.COMBAT_STALEMATE -> {
                enable(PolicyConditions.COMBAT_IN_ROOM, PolicyActions.COMBAT)
            }
            reason == ReplanReason.NO_PROGRESS || reason == ReplanReason.INTERVAL -> {
                enable(PolicyConditions.FRONTIER_AVAILABLE, PolicyActions.EXPLORE_UNVISITED)
            }
            FailureSignal.INVENTORY_FULL in observation.failureSignals -> {
                enable(PolicyConditions.INVENTORY_FULL, PolicyActions.MANAGE_INVENTORY)
            }
            FailureSignal.NO_WEAPON in observation.failureSignals -> {
                enable(PolicyConditions.NEEDS_WEAPON, PolicyActions.EQUIP_WEAPON)
                if (observation.situation.name == "LOOT_VISIBLE") {
                    enable(PolicyConditions.HAS_VISIBLE_ITEM, PolicyActions.NAVIGATE_ITEM)
                }
            }
        }

        val objective = current.objective
            ?: PolicyFallbackObjective.suggest(snapshot, context.knowledge)

        val patched = AgentPolicy(
            version = 4,
            phase = current.phase,
            objective = objective,
            params = current.params,
            rules = rules.values.toList(),
            notes = "llm-unavailable-minimal-patch:${reason.name.lowercase()} (strategy unchanged)",
        )
        return PolicyMerger.mergeWithBaseline(patched)
    }
}
