package ru.course.roguelike.policy.loop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.dsl.PolicyRule

class MacroDecisionRecorderTest {
    @Test
    fun `records objective params notes and rule changes`() {
        val policy = AgentPolicy(
            phase = "door_hunter",
            objective = PolicyObjective(ObjectiveKinds.ENTER_DOOR, "5,10", commitSteps = 30),
            params = PolicyParams(
                combatStyle = PolicyParams.COMBAT_CHASE,
                keyPriority = PolicyParams.DOORS_FIRST,
                exploreMode = PolicyParams.EXPLORE_DOOR_BIAS,
                riskLevel = PolicyParams.RISK_AGGRESSIVE,
            ),
            notes = "Rush nearest mob door",
            rules = listOf(
                PolicyRule(PolicyConditions.HAS_VISIBLE_ITEM, "navigate_item", enabled = true),
            ),
        )
        val decision = MacroDecisionRecorder.record(42, ReplanReason.INTERVAL, "ollama-replan-qwen", policy)
        assertEquals("door_hunter", decision.phase)
        assertEquals("enter_door", decision.objectiveKind)
        assertEquals("5,10", decision.objectiveTarget)
        assertTrue(decision.paramsSummary.contains("combat=chase"))
        assertTrue(decision.paramsSummary.contains("risk=aggressive"))
        assertEquals("Rush nearest mob door", decision.notes)
        assertTrue(decision.ruleChanges.any { it.contains("has_visible_item") })
        assertTrue(decision.trackerLine().contains("door_hunter"))
    }

    @Test
    fun `aggressive risk lowers combat stall threshold`() {
        val ctx = PolicyContext(combatStallSteps = 35)
        ctx.currentPolicy = AgentPolicy(params = PolicyParams(riskLevel = PolicyParams.RISK_AGGRESSIVE))
        assertEquals(24, ctx.effectiveCombatStallSteps())
        ctx.currentPolicy = AgentPolicy(params = PolicyParams(riskLevel = PolicyParams.RISK_CAUTIOUS))
        assertEquals(49, ctx.effectiveCombatStallSteps())
    }
}
