package ru.course.roguelike.agent.loop

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.llm.AgentDecisionClient
import ru.course.roguelike.agent.llm.FakeLlmClient
import ru.course.roguelike.agent.llm.HeuristicDecisionClient
import ru.course.roguelike.agent.llm.LlmClientFactory
import ru.course.roguelike.agent.mcp.MockMcpClient
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.SessionPhase
import kotlin.test.assertEquals

class AgentLoopTest {

    private val config = AgentConfig(
        llmProvider = "fake",
        llmApiKey = null,
        yandexFolderId = null,
        mcpCommand = emptyList(),
        mcpTransport = "mock",
        mcpServerUrl = "",
        maxToolCalls = 100,
        retryAttempts = 3,
    )

    class FakeLlmClientFactory(
        private val responses: List<ToolCallDecision?>,
        private val loop: Boolean = false,
    ) : LlmClientFactory() {

        override fun create(
            config: AgentConfig,
            fallback: AgentDecisionClient,
        ): AgentDecisionClient = FakeLlmClient(
            fallback = fallback,
            responses = responses,
            loop = loop
        )
    }

    @Test
    fun testFakeLLMCreation() = runTest {
        val fallback = HeuristicDecisionClient()
        val llm = FakeLlmClientFactory(responses = emptyList(), loop = false).create(config, fallback)
        assertTrue(llm is FakeLlmClient)
    }

    @Test
    fun `agent stops when max steps budget exceeded`() = runTest {
        val infiniteMoveEast = List(100) { moveEast }
        val mockMcp = MockMcpClient(width = 5, height = 5, exitGate = GridPos(4, 4), keysRequired = 0)
        val loop = AgentLoop(
            config = config,
            mcpFactory = { mockMcp },
            llmFactory = FakeLlmClientFactory(infiniteMoveEast, loop = true)
        )
        val request = AgentRunRequest(maxSteps = 5, sessionId = null, seed = null)
        val response = loop.run(request)

        assertEquals("STOPPED", response.status)
        assertTrue(response.stepsUsed <= 5)
        assertNotEquals(SessionPhase.LEVEL_COMPLETE.name, response.finalPhase)
    }

    @Test
    fun `agent detects loop and recovers via fallback`() = runTest {
        val loopingAction = moveEast
        val fakeLlm = FakeLlmClientFactory(listOf(loopingAction), loop = true)
        val mockMcp = MockMcpClient(width = 5, height = 5, exitGate = GridPos(4, 4), keysRequired = 0)

        val loop = AgentLoop(config, mcpFactory = { mockMcp }, llmFactory = fakeLlm)
        val response = loop.run(AgentRunRequest(maxSteps = 50))

        assertTrue(response.status in listOf("COMPLETE", "STOPPED"))

        assertTrue(response.toolCallLog.any { it.startsWith("fallback") })
    }

    @Test
    fun `agent terminates immediately on terminal phase`() = runTest {
        val fakeLlm = FakeLlmClientFactory(listOf(), loop = true)
        val mockMcp =
            MockMcpClient(
                width = 5,
                height = 5,
                exitGate = GridPos(4, 4),
                keysRequired = 0,
                phase = SessionPhase.LEVEL_COMPLETE.name
            )

        val loop = AgentLoop(config, mcpFactory = { mockMcp }, llmFactory = fakeLlm)
        val response = loop.run(AgentRunRequest(maxSteps = 50))

        assertTrue(response.status in listOf("COMPLETE", "STOPPED"))
        assertEquals(0, response.stepsUsed)
    }

    @Test
    fun `agent completes level by collecting keys and exiting`() = runTest {
        val actions = listOf(
            moveEast,
            moveEast,
            moveEast,
            moveNorth,
            moveNorth,
            moveNorth,
            interact,
        )
        val mockMcp = MockMcpClient(
            keysRequired = 0,
            exitGate = GridPos(4, 4),
            width = 5,
            height = 5,
        )
        val loop = AgentLoop(config, mcpFactory = { mockMcp }, llmFactory = FakeLlmClientFactory(actions, false))
        val request = AgentRunRequest(maxSteps = 30, sessionId = null, seed = null)
        val response = loop.run(request)
        assertEquals("COMPLETE", response.status)
        assertTrue(response.success)
        assertEquals(SessionPhase.LEVEL_COMPLETE.name, response.finalPhase)
    }

    companion object {

        private val moveEast = ToolCallDecision(
            tool = "game_act",
            arguments = buildJsonObject {
                put("action", JsonPrimitive("move_east"))
            }.mapValues { it.value },
        )
        private val moveNorth = ToolCallDecision(
            tool = "game_act",
            arguments = buildJsonObject {
                put("action", JsonPrimitive("move_north"))
            }.mapValues { it.value },
        )
        private val interact = ToolCallDecision(
            tool = "game_act",
            arguments = buildJsonObject {
                put("action", JsonPrimitive("interact"))
            }.mapValues { it.value },
        )
    }
}
