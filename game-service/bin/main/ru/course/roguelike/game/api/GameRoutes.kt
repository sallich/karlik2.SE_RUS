package ru.course.roguelike.game.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.course.roguelike.game.CreateSessionRequest
import ru.course.roguelike.game.PlayerActionRequest
import ru.course.roguelike.game.domain.StubGameEngine

private val engine = StubGameEngine()

fun Route.configureGameRoutes() {
    route("/sessions") {
        post {
            val request = call.receive<CreateSessionRequest>()
            val session = engine.createSession(request.seed)
            call.respond(session)
        }
        get("/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@get call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "sessionId required"),
                )
            val session = engine.getSession(sessionId)
            if (session == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound, mapOf("error" to "session not found"))
            } else {
                call.respond(session)
            }
        }
        post("/{sessionId}/actions") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "sessionId required"),
                )
            val request = call.receive<PlayerActionRequest>()
            val result = engine.applyAction(sessionId, request.action)
            if (result == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound, mapOf("error" to "session not found"))
            } else {
                call.respond(result)
            }
        }
    }
}
