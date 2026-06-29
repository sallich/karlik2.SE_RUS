package ru.course.roguelike.agent.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.UserMessage
import ru.course.roguelike.agent.config.AgentConfig

class MobDecisionServiceTest {
    private val baseConfig = AgentConfig(
        llmProvider = "yandex",
        llmApiKey = "fake-key",
        yandexFolderId = "fake-folder",
        mcpCommand = emptyList(),
        mcpTransport = "mock",
        mcpServerUrl = "",
        maxToolCalls = 100,
        retryAttempts = 3,
        maxMobToolCalls = 5
    )

    @Test
    fun `should use heuristic when provider is heuristic`() = runTest {
        val config = baseConfig.copy(llmProvider = "heuristic")
        val service = MobDecisionService(config)
        val request = MobDecideRequest(
            mobX = 10f,
            mobY = 10f,
            playerX = 15f,
            playerY = 15f,
            distance = 7.0f,
            playerHp = 100,
            mobId = 1,
        )
        val response = service.decide(request)
        assertEquals("chase", response.intent)
        assertEquals("heuristic", response.source)
    }

    @Test
    fun `should fallback to heuristic when budget exhausted`() = runTest {
        val config = baseConfig.copy(maxMobToolCalls = 2)
        val fakeLlm = FakeLlmClient(response = MobDecideResponse("chase", "fake"))
        val service = MobDecisionService(config, llm = fakeLlm)
        val request = MobDecideRequest(1L, 0f, 0f, 3f, 0f, 2.0f, 100)

        val response1 = service.decide(request)
        assertEquals("chase", response1.intent)
        val response2 = service.decide(request)
        assertEquals("chase", response2.intent)
        val response3 = service.decide(request)
        assertEquals("shoot", response3.intent)
        assertEquals("heuristic", response3.source)
    }

    @Test
    fun `should pass correct prompt and tools to LLM and parse response`() = runTest {
        val expectedResponse = MobDecideResponse("kite", "fake")
        val fakeLlm = FakeLlmClient(response = expectedResponse)
        val config = baseConfig.copy(maxMobToolCalls = 10)
        val service = MobDecisionService(config, llm = fakeLlm)

        val request = MobDecideRequest(
            mobId = 42L,
            mobX = 5.5f,
            mobY = 7.2f,
            playerX = 6.1f,
            playerY = 7.0f,
            distance = 0.8f,
            playerHp = 45
        )
        val result = service.decide(request)

        assertEquals(expectedResponse.intent, result.intent)
        assertEquals(expectedResponse.source, result.source)

        val userMessage = fakeLlm.lastMessages?.firstOrNull() as? UserMessage
        assertNotNull(userMessage)
        val prompt = userMessage.text
        assertTrue(prompt.contains("5.5") && prompt.contains("7.2"))
        assertTrue(prompt.contains("6.1") && prompt.contains("7.0"))
        assertTrue(prompt.contains("0.8"))
        assertTrue(prompt.contains("45"))

        val tools = fakeLlm.lastTools
        assertNotNull(tools)
        assertEquals(1, tools.size)
        val mobTool = tools.first()
        assertEquals("mob_action", mobTool.name)

        assertEquals(request, fakeLlm.lastRequest)

        val properties = mobTool.inputSchema["properties"]?.jsonObject
        assertNotNull(properties)
        assertTrue(properties.containsKey("intent"))
    }

    @Test
    fun `heuristic decision based on distance`() = runTest {
        val config = baseConfig.copy(llmProvider = "heuristic")
        val service = MobDecisionService(config)

        val chaseRequest = MobDecideRequest(1L, 0f, 0f, 4.5f, 0f, 4.5f, 100)
        assertEquals("chase", service.decide(chaseRequest).intent)

        val shootRequest = MobDecideRequest(1L, 0f, 0f, 2.0f, 0f, 2.0f, 100)
        assertEquals("shoot", service.decide(shootRequest).intent)

        val kiteRequest = MobDecideRequest(1L, 0f, 0f, 1.2f, 0f, 1.2f, 100)
        assertEquals("kite", service.decide(kiteRequest).intent)

        val idleRequest = MobDecideRequest(1L, 0f, 0f, 3.0f, 0f, 3.0f, 100)
        assertEquals("idle", service.decide(idleRequest).intent)
    }

    @Test
    fun `LLM receives correct coordinates and HP in prompt`() = runTest {
        val fakeLlm = FakeLlmClient()
        val config = baseConfig.copy(maxMobToolCalls = 10)
        val service = MobDecisionService(config, llm = fakeLlm)

        val request = MobDecideRequest(
            mobId = 7L,
            mobX = 12.3f,
            mobY = 4.7f,
            playerX = 1.0f,
            playerY = 8.9f,
            distance = 2.5f,
            playerHp = 78
        )
        service.decide(request)

        val userMsg = fakeLlm.lastMessages?.firstOrNull() as? UserMessage
        val text = userMsg?.text ?: ""
        assertTrue(text.contains("12.3") && text.contains("4.7"))
        assertTrue(text.contains("1.0") && text.contains("8.9"))
        assertTrue(text.contains("2.5"))
        assertTrue(text.contains("78"))
        assertFalse(text.contains("mobId"))
    }
}
