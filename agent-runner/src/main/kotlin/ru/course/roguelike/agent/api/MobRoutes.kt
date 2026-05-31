package ru.course.roguelike.agent.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.llm.MobDecisionService

fun Route.configureMobRoutes(config: AgentConfig) {
    val mobDecisions = MobDecisionService(config)

    post("/mob/decide") {
        val request = call.receive<MobDecideRequest>()
        val result = mobDecisions.decide(request)
        call.respond(result)
    }
}
