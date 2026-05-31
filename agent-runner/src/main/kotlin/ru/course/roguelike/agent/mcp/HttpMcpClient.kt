package ru.course.roguelike.agent.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.course.roguelike.agent.config.AgentConfig

@Serializable
private data class HttpToolCallRequest(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

@Serializable
private data class HttpContentBlock(val type: String = "text", val text: String)

@Serializable
private data class HttpToolCallResponse(
    val content: List<HttpContentBlock>,
    val isError: Boolean = false,
)

class HttpMcpClient(
    private val baseUrl: String,
    private val http: HttpClient = defaultClient(),
) : McpClient {
    override suspend fun callTool(name: String, arguments: Map<String, JsonElement>): McpToolResult {
        val response = http.post("$baseUrl/mcp/tools/call") {
            contentType(ContentType.Application.Json)
            setBody(HttpToolCallRequest(name, arguments))
        }.body<HttpToolCallResponse>()
        return McpToolResult(
            text = response.content.firstOrNull()?.text ?: "",
            isError = response.isError,
        )
    }

    override fun close() {
        http.close()
    }

    companion object {
        fun fromConfig(config: AgentConfig): HttpMcpClient =
            HttpMcpClient(config.mcpServerUrl.trimEnd('/'))

        private fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
