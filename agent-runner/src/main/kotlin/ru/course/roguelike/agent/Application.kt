package ru.course.roguelike.agent

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
import ru.course.roguelike.agent.api.configureAgentRoutes
import ru.course.roguelike.agent.config.AgentConfig

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8082
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

@Suppress("unused")
fun Application.module() {
    val config = AgentConfig.fromEnvironment()

    install(CallLogging) {
        level = Level.INFO
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = false
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
            call.respond(
                HealthResponse(
                    status = "UP",
                    service = "agent-runner",
                    llmProvider = config.llmProvider,
                    mcpTransport = config.mcpTransport,
                ),
            )
        }
        route("/api/v1/agent") {
            configureAgentRoutes(config)
        }
    }
}
