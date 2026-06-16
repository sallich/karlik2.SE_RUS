package ru.course.roguelike.policy

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import ru.course.roguelike.agent.tracker.LlmHealthChecker
import ru.course.roguelike.policy.api.configurePolicyAgentRoutes
import ru.course.roguelike.policy.api.configurePolicyTrackerRoutes
import ru.course.roguelike.policy.config.PolicyAgentConfig

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8083
    embeddedServer(Netty, port = port, module = Application::policyModule).start(wait = true)
}

@Suppress("unused")
fun Application.policyModule() {
    val config = PolicyAgentConfig.fromEnvironment()

    install(CallLogging) {
        level = Level.INFO
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = false
                encodeDefaults = true
            },
        )
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "internal error")),
            )
        }
    }
    routing {
        get("/health") {
            val llm = if (config.llmProvider == "ollama") {
                LlmHealthChecker.check(config.agent)
            } else {
                null
            }
            val ollamaOk = llm == null || (llm.reachable && llm.modelAvailable)
            call.respond(
                PolicyHealthResponse(
                    status = when {
                        llm == null -> "UP"
                        ollamaOk -> "UP"
                        llm.reachable -> "DEGRADED"
                        else -> "DEGRADED"
                    },
                    service = "policy-agent-runner",
                    llmProvider = config.llmProvider,
                    mcpTransport = config.mcpTransport,
                    ollamaBaseUrl = config.ollamaBaseUrl.takeIf { config.llmProvider == "ollama" },
                    ollamaModel = config.ollamaModel.takeIf { config.llmProvider == "ollama" },
                    ollamaFallbackModel = config.ollamaFallbackModel.takeIf { config.llmProvider == "ollama" },
                    requireLlm = config.requireLlm,
                    llm = llm,
                ),
            )
        }
        route("/api/v1/policy-agent") {
            configurePolicyAgentRoutes(config)
            configurePolicyTrackerRoutes(config)
        }
    }
}
