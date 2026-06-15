package ru.course.roguelike.agent

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.mcp.api.configureMcpRoutes
import ru.course.roguelike.mcp.protocol.McpToolRegistry

class AgentRunnerTest {
    @Test
    fun `health exposes agent-runner`() = testApplication {
            application { module() }
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("agent-runner"))
        }

    @Test
    fun `heuristic planner chooses game_act`() {
        val planner = KeyHuntPlanner()
        val decisions =
            planner.plan(
                snapshot = TestSnapshots.simpleRoom(),
                sessionId = "test-session",
            )
        val decision = decisions.first()
        assertTrue(decision.tool == "game_act" || decision.tool == "game_sync")
    }

    @Test
    fun `run endpoint accepts request with embedded stack`() {
        System.setProperty("SKIP_MOBS", "true")
        System.setProperty("SKIP_LLM_MOB", "true")
        val engine = GameEngine()
        val registry = McpToolRegistry(LocalGameSessionClient(engine))
        val mcp =
            embeddedServer(Netty, port = 0) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                routing {
                    route("/mcp") {
                        configureMcpRoutes(registry)
                    }
                }
            }.start(wait = false)
        val mcpPort =
            runBlocking {
                mcp.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        try {
            System.setProperty("MCP_TRANSPORT", "http")
            System.setProperty("MCP_SERVER_URL", "http://127.0.0.1:$mcpPort")
            System.setProperty("LLM_PROVIDER", "heuristic")
            System.setProperty("AGENT_MAX_TOOL_CALLS", "5")

            testApplication {
                application { module() }
                val response =
                    client.post("/api/v1/agent/run") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"seed":42,"maxSteps":5}""")
                    }
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"status\""))
            }
        } finally {
            mcp.stop(gracePeriodMillis = 200, timeoutMillis = 2_000)
            System.clearProperty("MCP_TRANSPORT")
            System.clearProperty("MCP_SERVER_URL")
            System.clearProperty("LLM_PROVIDER")
            System.clearProperty("AGENT_MAX_TOOL_CALLS")
            System.clearProperty("SKIP_MOBS")
            System.clearProperty("SKIP_LLM_MOB")
        }
    }
}
