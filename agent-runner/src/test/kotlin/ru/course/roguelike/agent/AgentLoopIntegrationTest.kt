package ru.course.roguelike.agent

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.loop.AgentLoop
import ru.course.roguelike.agent.mcp.InProcessMcpClient
import ru.course.roguelike.shared.model.SessionPhase
import java.util.concurrent.TimeUnit

class AgentLoopIntegrationTest {
    private lateinit var stack: EmbeddedRoguelikeStack.RunningServers

    @BeforeEach
    fun setUp() {
        System.setProperty("SKIP_MOBS", "true")
        System.setProperty("SKIP_LLM_MOB", "true")
        stack = EmbeddedRoguelikeStack.start()
    }

    @AfterEach
    fun tearDown() {
        EmbeddedRoguelikeStack.stop(stack)
        System.clearProperty("SKIP_MOBS")
        System.clearProperty("SKIP_LLM_MOB")
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun `heuristic agent completes level on fixed seed`() = runBlocking {
        val config = AgentConfig(
            llmProvider = "heuristic",
            llmApiKey = null,
            yandexFolderId = null,
            mcpCommand = emptyList(),
            mcpTransport = "http",
            mcpServerUrl = "http://localhost:8081",
            maxToolCalls = 800,
            retryAttempts = 1,
        )
        val loop = AgentLoop(
            config = config,
            mcpFactory = { InProcessMcpClient(stack.registry) },
        )
        val result = loop.run(AgentRunRequest(seed = 42L, maxSteps = 800))
        assertTrue(
            result.success || result.finalPhase == SessionPhase.LEVEL_COMPLETE.name,
            "expected LEVEL_COMPLETE, got phase=${result.finalPhase} status=${result.status}",
        )
    }
}
