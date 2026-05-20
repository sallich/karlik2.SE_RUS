package ru.course.roguelike.mcp.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.course.roguelike.mcp.McpToolCallRequest
import ru.course.roguelike.mcp.McpToolDescriptor
import ru.course.roguelike.mcp.McpToolsListResponse
import ru.course.roguelike.mcp.client.GameServiceClient
import ru.course.roguelike.mcp.protocol.McpToolRegistry

fun Route.configureMcpRoutes(gameClient: GameServiceClient) {
    val registry = McpToolRegistry(gameClient)

    get("/tools") {
        call.respond(
            McpToolsListResponse(
                tools = registry.descriptors(),
            ),
        )
    }

    post("/tools/call") {
        val request = call.receive<McpToolCallRequest>()
        val response = registry.invoke(request.name, request.arguments)
        call.respond(response)
    }

    route("/jsonrpc") {
        post {
            val request = call.receive<ru.course.roguelike.mcp.JsonRpcRequest>()
            val response = registry.handleJsonRpc(request)
            call.respond(response)
        }
    }
}

internal fun stubToolsList(): List<McpToolDescriptor> = listOf(
    McpToolDescriptor("game_new_session", "Create a new roguelike session with optional seed."),
    McpToolDescriptor("game_observe", "Get public observation for a session (stub)."),
    McpToolDescriptor("game_act", "Apply a player action in a session."),
)
