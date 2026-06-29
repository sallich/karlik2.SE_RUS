package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.config.PolicyAgentConfig
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.loop.ReplanReason
import ru.course.roguelike.policy.observation.PolicyObservation
import ru.course.roguelike.shared.dto.GameSnapshot

/** Static template policy — only when `LLM_PROVIDER=heuristic` (tests / eval baseline without Ollama). */
class HeuristicPolicyGenerator : PolicyGenerator {
    override suspend fun initialPolicy(snapshot: GameSnapshot, context: PolicyContext): PolicyGenResult {
        val raw = llmUnavailablePolicy(snapshot, context, "heuristic-no-llm")
        return PolicyGenResult(
            PolicyMerger.mergeWithBaseline(raw),
            "heuristic-fallback",
        )
    }

    override suspend fun replan(
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
        reason: ReplanReason,
    ): PolicyGenResult {
        val observation = context.lastObservation
            ?: PolicyObservation.observe(snapshot, context)
        val patched = PolicyDeterministicPatch.apply(current, observation, reason, context, snapshot)
        return PolicyGenResult(patched, "heuristic-minimal-patch-${reason.name.lowercase()}")
    }

    companion object {
        internal fun llmUnavailablePolicy(
            snapshot: GameSnapshot,
            context: PolicyContext,
            phase: String,
        ): AgentPolicy = DefaultPolicies.standard().copy(
            phase = phase,
            objective = PolicyFallbackObjective.suggest(snapshot, context.knowledge),
            notes = "LLM unavailable — survival fallback only; no strategic decisions from code",
        )
    }
}
