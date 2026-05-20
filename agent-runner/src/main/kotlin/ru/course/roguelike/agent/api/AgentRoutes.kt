package ru.course.roguelike.agent.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.AgentStatusResponse
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.loop.StubAgentLoop

private val stubLoop = StubAgentLoop()

fun Route.configureAgentRoutes(config: AgentConfig) {
    get("/status") {
        call.respond(
            AgentStatusResponse(
                mode = "stub",
                message = "Agent loop will connect via ${config.mcpTransport} MCP to mcp-server " +
                    "and ${config.llmProvider} for LLM. No game state access from this service.",
                budgetRemaining = config.maxToolCalls,
            ),
        )
    }

    post("/run") {
        val request = call.receive<AgentRunRequest>()
        val result = stubLoop.planRun(request, config)
        call.respond(result)
    }
}
