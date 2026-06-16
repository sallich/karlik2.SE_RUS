package ru.course.roguelike.policy.loop

import kotlinx.serialization.Serializable
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.DefaultPolicies

/**
 * One LLM (or fallback) macro decision — visible in tracker and run response.
 * Not per-step: recorded when initial policy or a replan is applied.
 */
@Serializable
data class MacroDecision(
    val step: Int,
    val reason: String,
    val source: String,
    val phase: String? = null,
    val objectiveKind: String? = null,
    val objectiveTarget: String? = null,
    val commitSteps: Int? = null,
    val paramsSummary: String = "",
    val notes: String? = null,
    /** Rules whose enabled/action differ from the static baseline (LLM patch fingerprint). */
    val ruleChanges: List<String> = emptyList(),
) {
    fun trackerLine(): String = buildString {
        append("macro[$step/$reason]: $source")
        phase?.let { append(" · phase=$it") }
        objectiveKind?.let { kind ->
            append(" · obj=$kind")
            objectiveTarget?.let { append("@$it") }
            commitSteps?.let { append(" commit=$it") }
        }
        if (paramsSummary.isNotBlank()) append(" · $paramsSummary")
        notes?.takeIf { it.isNotBlank() }?.let { append(" · \"$it\"") }
        if (ruleChanges.isNotEmpty()) append(" · rules=[${ruleChanges.joinToString()}]")
    }
}

object MacroDecisionRecorder {
    private val baselineByClause = DefaultPolicies.standardRules().associateBy { it.whenClause }

    fun record(
        step: Int,
        reason: ReplanReason,
        source: String,
        policy: AgentPolicy,
    ): MacroDecision {
        val obj = policy.objective
        val p = policy.params
        return MacroDecision(
            step = step,
            reason = reason.name.lowercase(),
            source = source,
            phase = policy.phase,
            objectiveKind = obj?.kind,
            objectiveTarget = obj?.target,
            commitSteps = obj?.commitSteps,
            paramsSummary = "combat=${p.combatStyle} keys=${p.keyPriority} explore=${p.exploreMode} risk=${p.riskLevel}",
            notes = policy.notes?.trim()?.take(120),
            ruleChanges = diffRules(policy),
        )
    }

    private fun diffRules(policy: AgentPolicy): List<String> {
        val byClause = policy.rules.associateBy { it.whenClause }
        return policy.rules.mapNotNull { rule ->
            val base = baselineByClause[rule.whenClause] ?: return@mapNotNull null
            when {
                rule.enabled != base.enabled ->
                    "${rule.whenClause}:${if (rule.enabled) "on" else "off"}"
                rule.enabled && rule.action != base.action ->
                    "${rule.whenClause}→${rule.action}"
                else -> null
            }
        }
    }
}
