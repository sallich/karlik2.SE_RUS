package ru.course.roguelike.policy.api

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.runBlocking
import ru.course.roguelike.agent.tracker.AgentLiveTracker
import ru.course.roguelike.agent.tracker.LlmHealthChecker
import ru.course.roguelike.policy.config.PolicyAgentConfig

fun Route.configurePolicyTrackerRoutes(config: PolicyAgentConfig) {
    get("/live") {
        call.respond(AgentLiveTracker.snapshot())
    }

    get("/llm/health") {
        call.respond(runBlocking { LlmHealthChecker.check(config.agent) })
    }

    get("/tracker") {
        val html = this::class.java.classLoader
            .getResourceAsStream("policy-tracker.html")
            ?.readBytes()
            ?.decodeToString()
            ?: error("policy-tracker.html missing")
        call.respondText(html, ContentType.Text.Html)
    }
}
