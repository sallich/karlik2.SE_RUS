package ru.course.roguelike.game.domain.ai

import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.combat.MobEntity
import ru.course.roguelike.game.infrastructure.agent.AgentRunnerMobClient
import ru.course.roguelike.shared.model.MobKind

class LlmGuardBehaviorTest {
    @Test
    fun `falls back to shooter when agent-runner unavailable`() {
        val mobClient = mockk<AgentRunnerMobClient>()
        coEvery { mobClient.decide(any()) } returns null
        val behavior = LlmGuardBehavior(mobClient)
        val mob = MobEntity.LlmGuardMob(
            id = 1L,
            x = 5f,
            y = 5f,
            behavior = behavior,
        )
        val context = MobDecisionContext(
            mob = mob,
            playerX = 6f,
            playerY = 5f,
            distanceToPlayer = 1f,
            playerHp = 80,
        )
        val intent = behavior.decide(context)
        assertEquals(MobIntent.ShootPlayer, intent)
    }

    @Test
    fun `uses LLM intent when agent-runner responds`() {
        val mobClient = mockk<AgentRunnerMobClient>()
        coEvery { mobClient.decide(any()) } returns MobIntent.KitePlayer
        val behavior = LlmGuardBehavior(mobClient)
        val mob = MobEntity.LlmGuardMob(
            id = 2L,
            x = 5f,
            y = 5f,
            behavior = behavior,
        )
        val context = MobDecisionContext(
            mob = mob,
            playerX = 8f,
            playerY = 5f,
            distanceToPlayer = 3f,
            playerHp = 80,
        )
        assertEquals(MobIntent.KitePlayer, behavior.decide(context))
    }

    @Test
    fun `mobBehaviorFor maps LLM_GUARD`() {
        assertTrue(mobBehaviorFor(MobKind.LLM_GUARD) is LlmGuardBehavior)
    }
}
