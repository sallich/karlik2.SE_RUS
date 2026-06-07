package ru.course.roguelike.mcp

import kotlinx.serialization.json.Json
import ru.course.roguelike.mcp.client.GameServiceClient
import ru.course.roguelike.mcp.protocol.McpToolRegistry

/**
 * MCP over stdio: one JSON-RPC object per line on stdin/stdout.
 * Usage: java -jar mcp-server.jar stdio
 */
object McpStdioMain {
    private val json = Json { ignoreUnknownKeys = true }

    fun run() {
        val gameBaseUrl = System.getenv("GAME_SERVICE_URL") ?: "http://localhost:8080"
        val gameClient = GameServiceClient(gameBaseUrl)
        val registry = McpToolRegistry(gameClient)
        System.err.println("mcp-server stdio ready, game-service=$gameBaseUrl")

        generateSequence { readLine() }.forEach { line ->
            if (line.isNotBlank()) {
                handleLine(registry, line)
            }
        }
        gameClient.close()
    }

    private fun handleLine(registry: McpToolRegistry, line: String) {
        val request = runCatching { json.decodeFromString<JsonRpcRequest>(line) }.getOrNull()
        if (request == null) {
            val err = JsonRpcResponse(
                id = null,
                error = JsonRpcError(code = -32700, message = "Parse error"),
            )
            println(json.encodeToString(err))
            return
        }
        val response = kotlinx.coroutines.runBlocking {
            registry.handleJsonRpc(request)
        }
        println(json.encodeToString(response))
    }
}
