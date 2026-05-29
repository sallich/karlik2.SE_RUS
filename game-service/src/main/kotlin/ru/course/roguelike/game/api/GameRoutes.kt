package ru.course.roguelike.game.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.course.roguelike.game.CreateSessionRequest
import ru.course.roguelike.game.PlayerActionRequest
import ru.course.roguelike.game.application.ActionResult
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.shared.dto.InputSyncRequest

private val engine = GameEngine()

fun Route.configureGameRoutes() {
    route("/sessions") {
        post { createSession(call) }
        get("/{sessionId}") { getSession(call) }
        get("/{sessionId}/observe") { observeSession(call) }
        post("/{sessionId}/sync") { syncSession(call) }
        post("/{sessionId}/actions") { applySessionAction(call) }
    }
}

private suspend fun createSession(call: ApplicationCall) {
    val request = call.receive<CreateSessionRequest>()
    val snapshot = engine.createSession(request.seed)
    call.respond(snapshot)
}

private suspend fun getSession(call: ApplicationCall) {
    respondSnapshot(call, call.requireSessionId() ?: return)
}

private suspend fun observeSession(call: ApplicationCall) {
    respondSnapshot(call, call.requireSessionId() ?: return)
}

private suspend fun syncSession(call: ApplicationCall) {
    val sessionId = call.requireSessionId() ?: return
    val request = call.receive<InputSyncRequest>()
    respondActionResult(call, engine.syncInput(sessionId, request))
}

private suspend fun applySessionAction(call: ApplicationCall) {
    val sessionId = call.requireSessionId() ?: return
    val request = call.receive<PlayerActionRequest>()
    val result = engine.applyAction(sessionId, request.action) ?: run {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "session not found"))
        return
    }
    call.respond(result.response.copy(snapshot = result.snapshot))
}

private suspend fun ApplicationCall.requireSessionId(): String? {
    val sessionId = parameters["sessionId"]
    if (sessionId == null) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "sessionId required"))
    }
    return sessionId
}

private suspend fun respondSnapshot(call: ApplicationCall, sessionId: String) {
    val snapshot = engine.getSnapshot(sessionId)
    if (snapshot == null) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "session not found"))
    } else {
        call.respond(snapshot)
    }
}

private suspend fun respondActionResult(call: ApplicationCall, result: ActionResult?) {
    if (result == null) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "session not found"))
    } else {
        call.respond(result.response)
    }
}
