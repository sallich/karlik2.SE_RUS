package ru.course.roguelike.policy.dsl

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrategyArchetypesTest {
    @Test
    fun `prompt examples list all archetype phases`() {
        val text = StrategyArchetypes.promptExamples()
        StrategyArchetypes.ALL.forEach { archetype ->
            assertTrue(text.contains(archetype.phase), "missing phase ${archetype.phase} in prompt examples")
        }
    }
}
