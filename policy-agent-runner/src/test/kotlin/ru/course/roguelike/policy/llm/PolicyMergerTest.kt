package ru.course.roguelike.policy.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNull
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyActions
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.dsl.PolicyRule

class PolicyMergerTest {
    @Test
    fun `merge v3 preserves params`() {
        val llm = AgentPolicy(
            version = 3,
            phase = "explore_keys",
            params = PolicyParams(exploreMode = PolicyParams.EXPLORE_FRONTIER),
            rules = DefaultPolicies.standardRules(),
        )
        val merged = PolicyMerger.mergeWithBaseline(llm)
        assertEquals(PolicyParams.EXPLORE_FRONTIER, merged.params.exploreMode)
        assertEquals("explore_keys", merged.phase)
    }

    @Test
    fun `delta patch updates single rule`() {
        val current = PolicyMerger.mergeWithBaseline(DefaultPolicies.standard())
        val patch = PolicyPatchRequest(
            patch = listOf(
                PolicyRule(PolicyConditions.STUCK, PolicyActions.UNSTUCK, enabled = true),
            ),
            params = PolicyParams(unstuckMode = PolicyParams.UNSTUCK_RETREAT),
        )
        val merged = PolicyMerger.applyPatch(current, patch)
        val stuck = merged.rules.first { it.whenClause == PolicyConditions.STUCK }
        assertTrue(stuck.enabled)
        assertEquals(PolicyParams.UNSTUCK_RETREAT, merged.params.unstuckMode)
    }

    @Test
    fun `canonical order includes corner_trapped`() {
        assertTrue(PolicyConditions.CORNER_TRAPPED in PolicyMerger.CANONICAL_ORDER)
    }

    @Test
    fun `mergeWithBaseline carries objective`() {
        val llm = AgentPolicy(
            version = 4,
            objective = PolicyObjective(ObjectiveKinds.ENTER_DOOR, target = "3,4", commitSteps = 15),
            rules = DefaultPolicies.standardRules(),
        )
        val merged = PolicyMerger.mergeWithBaseline(llm)
        assertEquals(ObjectiveKinds.ENTER_DOOR, merged.objective?.kind)
        assertEquals("3,4", merged.objective?.target)
        assertEquals(15, merged.objective?.commitSteps)
    }

    @Test
    fun `delta patch sets objective without touching rules`() {
        val current = PolicyMerger.mergeWithBaseline(DefaultPolicies.standard())
        val patch = PolicyPatchRequest(
            objective = PolicyObjective(ObjectiveKinds.EXPLORE, target = "7,2"),
        )
        val merged = PolicyMerger.applyPatch(current, patch)
        assertEquals(ObjectiveKinds.EXPLORE, merged.objective?.kind)
        assertEquals("7,2", merged.objective?.target)
    }

    @Test
    fun `sanitizeObjective drops unknown kind and clamps commit window`() {
        assertNull(PolicyPromptBuilder.sanitizeObjective(PolicyObjective("teleport", "1,1")))
        val tooLong = PolicyPromptBuilder.sanitizeObjective(
            PolicyObjective(ObjectiveKinds.EXPLORE, "1,1", commitSteps = 9999),
        )
        assertEquals(ObjectiveKinds.MAX_COMMIT_STEPS, tooLong?.commitSteps)
        assertNull(
            PolicyPromptBuilder.sanitizeObjective(
                PolicyObjective(ObjectiveKinds.ENTER_DOOR, target = "not-a-cell"),
            ),
            "targeted objective without parseable cell must be dropped",
        )
        assertNull(
            PolicyPromptBuilder.sanitizeObjective(
                PolicyObjective(ObjectiveKinds.EXPLORE, target = "-"),
            ),
            "explore with placeholder target must be dropped",
        )
    }

    @Test
    fun `cleanNavigationFallbacks resets code-injected rule toggles`() {
        val polluted = PolicyMerger.mergeWithBaseline(
            DefaultPolicies.standard().copy(
                rules = DefaultPolicies.standardRules().map { rule ->
                    when (rule.whenClause) {
                        PolicyConditions.STUCK -> rule.copy(enabled = false)
                        PolicyConditions.FRONTIER_AVAILABLE -> rule.copy(enabled = true)
                        else -> rule
                    }
                },
            ),
        )
        val cleaned = PolicyMerger.cleanNavigationFallbacks(polluted)
        assertTrue(cleaned.rules.first { it.whenClause == PolicyConditions.STUCK }.enabled)
        assertTrue(!cleaned.rules.first { it.whenClause == PolicyConditions.FRONTIER_AVAILABLE }.enabled)
    }
}
