package ru.course.roguelike.policy.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.runBlocking
import ru.course.roguelike.policy.PolicyAgentStatusResponse
import ru.course.roguelike.policy.PolicyRunRequest
import ru.course.roguelike.policy.config.PolicyAgentConfig
import ru.course.roguelike.policy.loop.PolicyAgentLoop

fun Route.configurePolicyAgentRoutes(config: PolicyAgentConfig) {
    val loop = PolicyAgentLoop(config)

    get("/status") {
        call.respond(
            PolicyAgentStatusResponse(
                mode = config.llmProvider,
                message = "Policy DSL agent via ${config.mcpTransport} MCP; macro LLM=${config.llmProvider}",
                budgetRemaining = config.maxToolCalls,
            ),
        )
    }

    post("/run") {
        val request = call.receive<PolicyRunRequest>()
        val result = runBlocking { loop.run(request) }
        call.respond(result)
    }
}
