package ru.course.roguelike.agent

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.loop.StubAgentLoop

class AgentRunnerTest {
    @Test
    fun `health exposes agent-runner`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("agent-runner"))
    }

    @Test
    fun `stub run returns planned steps`() {
        val config = AgentConfig(
            llmProvider = "stub",
            llmApiKey = null,
            mcpCommand = listOf("echo"),
            mcpTransport = "stdio",
            maxToolCalls = 50,
            retryAttempts = 3,
        )
        val result = StubAgentLoop().planRun(
            AgentRunRequest(seed = 1L, maxSteps = 5),
            config,
        )
        assertEquals(5, result.stepsPlanned)
        assertEquals("STUB", result.status)
    }

    @Test
    fun `run endpoint accepts request`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/agent/run") {
            contentType(ContentType.Application.Json)
            setBody("""{"seed":99,"maxSteps":3}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("STUB"))
    }
}
