package ru.course.roguelike.agent.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.runBlocking
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.AgentStatusResponse
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.loop.AgentLoop

fun Route.configureAgentRoutes(config: AgentConfig) {
    val agentLoop = AgentLoop(config)

    get("/status") {
        call.respond(
            AgentStatusResponse(
                mode = config.llmProvider,
                message = "Agent connects via ${config.mcpTransport} MCP; LLM=${config.llmProvider}",
                budgetRemaining = config.maxToolCalls,
            ),
        )
    }

    post("/run") {
        val request = call.receive<AgentRunRequest>()
        val result = runBlocking { agentLoop.run(request) }
        call.respond(result)
    }
}
