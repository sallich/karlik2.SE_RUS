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
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse

private val engine = GameEngine()

fun Route.configureGameRoutes() {
    route("/sessions") {
        post {
            val request = call.receive<CreateSessionRequest>()
            val snapshot = engine.createSession(request.seed)
            call.respond(snapshot)
        }
        get("/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
                ?: return@get call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "sessionId required"),
                )
            val snapshot = engine.getSnapshot(sessionId)
            if (snapshot == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound, mapOf("error" to "session not found"))
            } else {
                call.respond(snapshot)
            }
        }
        get("/{sessionId}/observe") {
            val sessionId = call.parameters["sessionId"]
                ?: return@get call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "sessionId required"),
                )
            val snapshot = engine.getSnapshot(sessionId)
            if (snapshot == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound, mapOf("error" to "session not found"))
            } else {
                call.respond(snapshot)
            }
        }
        post("/{sessionId}/sync") {
            val sessionId = call.parameters["sessionId"]
                ?: return@post call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "sessionId required"),
                )
            val request = call.receive<InputSyncRequest>()
            val result = engine.syncInput(sessionId, request)
            if (result == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound, mapOf("error" to "session not found"))
            } else {
                call.respond(result.response)
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
                call.respond(result.response.copy(snapshot = result.snapshot))
            }
        }
    }
}
