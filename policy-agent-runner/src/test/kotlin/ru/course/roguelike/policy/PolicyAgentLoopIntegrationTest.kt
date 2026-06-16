package ru.course.roguelike.policy

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.loop.AgentLoop
import ru.course.roguelike.policy.config.PolicyAgentConfig
import ru.course.roguelike.policy.loop.PolicyAgentLoop
import ru.course.roguelike.shared.model.SessionPhase
import java.util.concurrent.TimeUnit

class PolicyAgentLoopIntegrationTest {
    private lateinit var stack: PolicyIntegrationStack.Running

    @BeforeEach
    fun setUp() {
        System.setProperty("SKIP_MOBS", "true")
        System.setProperty("SKIP_LLM_MOB", "true")
        stack = PolicyIntegrationStack.start()
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        System.clearProperty("SKIP_MOBS")
        System.clearProperty("SKIP_LLM_MOB")
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `step agent baseline on same stack`() = runBlocking {
        val agentConfig = AgentConfig(
            llmProvider = "heuristic",
            llmApiKey = null,
            yandexFolderId = null,
            ollamaBaseUrl = "http://localhost:11434",
            ollamaModel = "qwen2.5:3b",
            ollamaFallbackModel = "qwen2.5:0.5b",
            llmRequestTimeoutMs = 120_000L,
            llmFallbackTimeoutMs = 45_000L,
            useHeuristicFallback = false,
            mcpCommand = emptyList(),
            mcpTransport = "http",
            mcpServerUrl = "http://localhost:8081",
            maxToolCalls = 1000,
            retryAttempts = 1,
            llmMaxHistoryMessages = 16,
            ollamaNumCtx = 8192,
            ollamaNumPredict = 256,
        )
        val loop = AgentLoop(
            config = agentConfig,
            mcpFactory = { _ -> InProcessPolicyMcpClient(stack.registry) },
        )
        val result = loop.run(AgentRunRequest(seed = 42L, maxSteps = 1000))
        assertTrue(result.success, "step agent: phase=${result.finalPhase} steps=${result.stepsUsed}")
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `explore-only policy completes level on fixed seed`() = runBlocking {
        val config = testConfig()
        val loop = PolicyAgentLoop(
            config = config,
            mcpFactory = { InProcessPolicyMcpClient(stack.registry) },
        )
        val exploreOnly = ru.course.roguelike.policy.dsl.AgentPolicy(
            rules = listOf(
                ru.course.roguelike.policy.dsl.PolicyRule(
                    ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE,
                    ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
                ),
            ),
        )
        val result = loop.runWithPolicy(PolicyRunRequest(seed = 42L, maxSteps = 5000), exploreOnly)
        assertTrue(
            result.success,
            "explore-only: phase=${result.finalPhase} steps=${result.stepsUsed}",
        )
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `minimal navigation policy completes level`() = runBlocking {
        val config = testConfig()
        val loop = PolicyAgentLoop(
            config = config,
            mcpFactory = { InProcessPolicyMcpClient(stack.registry) },
        )
        val minimal = ru.course.roguelike.policy.dsl.AgentPolicy(
            rules = listOf(
                ru.course.roguelike.policy.dsl.PolicyRule(
                    ru.course.roguelike.policy.dsl.PolicyConditions.CAN_INTERACT,
                    ru.course.roguelike.policy.dsl.PolicyActions.INTERACT,
                ),
                ru.course.roguelike.policy.dsl.PolicyRule(
                    ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_KEYS,
                    ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
                ),
                ru.course.roguelike.policy.dsl.PolicyRule(
                    ru.course.roguelike.policy.dsl.PolicyConditions.HAS_ALL_KEYS,
                    ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_EXIT,
                ),
                ru.course.roguelike.policy.dsl.PolicyRule(
                    ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE,
                    ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
                ),
            ),
        )
        val result = loop.runWithPolicy(PolicyRunRequest(seed = 42L, maxSteps = 5000), minimal)
        assertTrue(result.success, "minimal: phase=${result.finalPhase} steps=${result.stepsUsed}")
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `heuristic policy agent completes level on fixed seed`() = runBlocking {
        val config = testConfig()
        val loop = PolicyAgentLoop(
            config = config,
            mcpFactory = { InProcessPolicyMcpClient(stack.registry) },
        )
        val result = loop.run(PolicyRunRequest(seed = 42L, maxSteps = 5000))
        assertTrue(
            result.success || result.finalPhase == SessionPhase.LEVEL_COMPLETE.name,
            "expected LEVEL_COMPLETE, got phase=${result.finalPhase} status=${result.status} steps=${result.stepsUsed}",
        )
    }

    private fun testConfig() = PolicyAgentConfig(
        agent = AgentConfig(
            llmProvider = "heuristic",
            llmApiKey = null,
            yandexFolderId = null,
            ollamaBaseUrl = "http://localhost:11434",
            ollamaModel = "qwen2.5:3b",
            ollamaFallbackModel = "qwen2.5:0.5b",
            llmRequestTimeoutMs = 120_000L,
            llmFallbackTimeoutMs = 45_000L,
            useHeuristicFallback = false,
            mcpCommand = emptyList(),
            mcpTransport = "http",
            mcpServerUrl = "http://localhost:8081",
            maxToolCalls = 5000,
            retryAttempts = 1,
            llmMaxHistoryMessages = 16,
            ollamaNumCtx = 8192,
            ollamaNumPredict = 256,
        ),
        maxToolCalls = 5000,
        stuckThreshold = 3,
        replanEverySteps = 40,
        stuckReplanCooldown = 15,
        noProgressSteps = 25,
        strictFairPlay = false,
        requireLlm = false,
    )
}
