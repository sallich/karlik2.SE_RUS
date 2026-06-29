package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.dsl.PolicyObjective

/** Outcome of parsing an LLM policy response. */
sealed class PolicyLlmParseResult {
    data class Ok(val policy: AgentPolicy) : PolicyLlmParseResult()

    data class Failed(val failure: PolicyLlmParseFailure) : PolicyLlmParseResult()
}

/** Why an LLM response could not become a macro policy. */
data class PolicyLlmParseFailure(
    val kind: Kind,
    val detail: String,
    val rawSnippet: String,
) {
    enum class Kind {
        EMPTY,
        UNPARSEABLE,
        MISSING_OBJECTIVE,
        INVALID_OBJECTIVE,
    }
}

object PolicyLlmParser {
    fun parseAndMerge(content: String, current: AgentPolicy?): PolicyLlmParseResult {
        val snippet = content.trim().take(500)
        if (snippet.isEmpty()) {
            return fail(PolicyLlmParseFailure.Kind.EMPTY, "empty response", snippet)
        }

        val patchRaw = PolicyJson.parsePatch(content)
        val patch = patchRaw?.let { PolicyPromptBuilder.sanitizePatch(it) }
        if (patchRaw != null && patch != null) {
            val hasDelta = patchRaw.patch.isNotEmpty() ||
                patchRaw.objective != null ||
                patchRaw.params != null ||
                patchRaw.phase != null ||
                !patchRaw.notes.isNullOrBlank()
            if (hasDelta) {
                val base = current ?: PolicyMerger.mergeWithBaseline(DefaultPolicies.standard())
                val merged = PolicyMerger.applyPatch(base, patch)
                val withObjective = if (current != null && patch.objective == null && current.objective != null) {
                    merged.copy(objective = current.objective)
                } else {
                    merged
                }
                return finalize(withObjective, patchRaw.objective, snippet)
            }
        }

        if (PolicyJson.extractJsonObject(content) == null) {
            val detail = when {
                content.contains("```") ->
                    "no parseable JSON object (reply used markdown code fences — send raw JSON only)"
                else -> "no valid JSON object found"
            }
            return fail(PolicyLlmParseFailure.Kind.UNPARSEABLE, detail, snippet)
        }

        val full = PolicyJson.parse(content)
        if (full == null) {
            return fail(
                PolicyLlmParseFailure.Kind.UNPARSEABLE,
                "JSON object found but schema invalid (need version=4, objective, params)",
                snippet,
            )
        }

        return finalize(full, full.objective, snippet)
    }

    private fun finalize(raw: AgentPolicy, rawObjective: PolicyObjective?, snippet: String): PolicyLlmParseResult {
        val merged = PolicyMerger.mergeWithBaseline(PolicyPromptBuilder.sanitize(raw))
        if (merged.objective != null) {
            return PolicyLlmParseResult.Ok(merged)
        }
        return when {
            rawObjective == null -> fail(
                PolicyLlmParseFailure.Kind.MISSING_OBJECTIVE,
                "objective field is required for initial macro policy",
                snippet,
            )
            else -> fail(
                PolicyLlmParseFailure.Kind.INVALID_OBJECTIVE,
                objectiveRejectionDetail(rawObjective),
                snippet,
            )
        }
    }

    private fun objectiveRejectionDetail(raw: PolicyObjective): String {
        val kind = raw.kind.lowercase()
        val target = raw.target?.trim().orEmpty()
        return buildString {
            append("objective present but invalid after validation")
            append(" (kind=$kind")
            if (target.isNotEmpty()) append(", target=$target")
            append(") — target must be concrete \"x,y\" from the brief; explore/enter_door/reach_* need a valid cell")
        }
    }

    private fun fail(kind: PolicyLlmParseFailure.Kind, detail: String, snippet: String): PolicyLlmParseResult.Failed =
        PolicyLlmParseResult.Failed(PolicyLlmParseFailure(kind, detail, snippet))
}
