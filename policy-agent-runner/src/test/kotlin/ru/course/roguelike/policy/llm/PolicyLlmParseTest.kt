package ru.course.roguelike.policy.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyObjective

class PolicyLlmParseTest {

    @Test
    fun `parseAndMerge accepts full policy with objective`() {
        val json = """
            {"version":4,"phase":"EXPLORATION","objective":{"kind":"explore","target":"7,3","commitSteps":20},
            "params":{"exploreMode":"frontier","unstuckMode":"retreat","doorAggression":"normal",
            "combatStyle":"kite","keyPriority":"doors_first","riskLevel":"balanced"},
            "rules":[],"notes":"test"}
        """.trimIndent()
        val result = PolicyLlmParser.parseAndMerge(json, current = null)
        assertTrue(result is PolicyLlmParseResult.Ok)
        val policy = (result as PolicyLlmParseResult.Ok).policy
        assertEquals(ObjectiveKinds.EXPLORE, policy.objective?.kind)
        assertEquals("7,3", policy.objective?.target)
    }

    @Test
    fun `parseAndMerge rejects markdown fenced json without object`() {
        val result = PolicyLlmParser.parseAndMerge("```json\n", current = null)
        assertTrue(result is PolicyLlmParseResult.Failed)
        val failure = (result as PolicyLlmParseResult.Failed).failure
        assertEquals(PolicyLlmParseFailure.Kind.UNPARSEABLE, failure.kind)
    }

    @Test
    fun `parseAndMerge rejects json without objective on initial`() {
        val json = """{"version":4,"phase":"X","params":{"exploreMode":"frontier",
            "unstuckMode":"retreat","doorAggression":"normal","combatStyle":"kite",
            "keyPriority":"doors_first","riskLevel":"balanced"},"rules":[],"notes":"no objective"}"""
        val result = PolicyLlmParser.parseAndMerge(json, current = null)
        assertTrue(result is PolicyLlmParseResult.Failed)
        assertEquals(
            PolicyLlmParseFailure.Kind.MISSING_OBJECTIVE,
            (result as PolicyLlmParseResult.Failed).failure.kind,
        )
    }

    @Test
    fun `parseAndMerge rejects invalid objective target`() {
        val json = """{"version":4,"objective":{"kind":"explore","target":"-","commitSteps":20},
            "params":{"exploreMode":"frontier","unstuckMode":"retreat","doorAggression":"normal",
            "combatStyle":"kite","keyPriority":"doors_first","riskLevel":"balanced"},
            "rules":[],"notes":"bad target"}"""
        val result = PolicyLlmParser.parseAndMerge(json, current = null)
        assertTrue(result is PolicyLlmParseResult.Failed)
        assertEquals(
            PolicyLlmParseFailure.Kind.INVALID_OBJECTIVE,
            (result as PolicyLlmParseResult.Failed).failure.kind,
        )
    }

    @Test
    fun `parseAndMerge allows replan delta without objective when current has one`() {
        val current = DefaultPolicies.standard().copy(
            objective = PolicyObjective(ObjectiveKinds.EXPLORE, "7,3", 20),
        )
        val json = """{"version":4,"patch":[],"params":{"combatStyle":"chase"},"notes":"tweak combat"}"""
        val result = PolicyLlmParser.parseAndMerge(json, current = current)
        assertTrue(result is PolicyLlmParseResult.Ok)
        assertEquals("7,3", (result as PolicyLlmParseResult.Ok).policy.objective?.target)
    }

    @Test
    fun `jsonCorrectionMessage mentions problem and objective requirement`() {
        val msg = PolicyPromptBuilder.jsonCorrectionMessage(
            PolicyLlmParseFailure(
                kind = PolicyLlmParseFailure.Kind.MISSING_OBJECTIVE,
                detail = "objective field is required",
                rawSnippet = """{"version":4,"notes":"oops"}""",
            ),
        )
        assertTrue(msg.contains("CORRECTION REQUIRED"))
        assertTrue(msg.contains("objective"))
        assertNotNull(msg)
    }
}
