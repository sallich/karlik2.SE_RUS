package ru.course.roguelike.agent.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.shared.mcp.McpTool

@Serializable
private data class HttpToolCallRequest(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

@Serializable
private data class HttpContentBlock(
    val type: String = "text",
    val text: String,
)

@Serializable
private data class HttpToolCallResponse(
    val content: List<HttpContentBlock>,
    val isError: Boolean = false,
)

@Serializable
private data class HttpToolListResponse(
    val tools: List<HttpToolDescriptor>,
)

@Serializable
private data class HttpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JsonElement,
)

class HttpMcpClient(
    private val baseUrl: String,
    private val http: HttpClient = defaultClient(),
) : McpClient {
    private val log = LoggerFactory.getLogger(HttpMcpClient::class.java)

    override suspend fun callTool(
        name: String,
        arguments: Map<String, JsonElement>,
    ): McpToolResult {
        val response =
            http
                .post("$baseUrl/mcp/tools/call") {
                    contentType(ContentType.Application.Json)
                    setBody(HttpToolCallRequest(name, arguments))
                }.body<HttpToolCallResponse>()
        return McpToolResult(
            text = response.content.firstOrNull()?.text ?: "",
            isError = response.isError,
        )
    }

    override suspend fun getTools(): List<McpTool> {
        val response =
            try {
                http
                    .get("$baseUrl/mcp/tools") {
                        contentType(ContentType.Application.Json)
                    }.body<HttpToolListResponse>()
            } catch (e: ClientRequestException) {
                log.warn("Client request error fetching tools: ${e.response.status}", e)
                return emptyList()
            } catch (e: ServerResponseException) {
                log.warn("Server response error fetching tools: ${e.response.status}", e)
                return emptyList()
            } catch (e: IOException) {
                log.warn("IO error fetching tools: ${e.message}", e)
                return emptyList()
            }
        return response.tools.mapNotNull { tool ->
            val schema = tool.inputSchema as? JsonObject ?: return@mapNotNull null
            McpTool(
                name = tool.name,
                description = tool.description,
                inputSchema = schema,
            )
        }
    }

    override fun close() {
        http.close()
    }

    companion object {
        fun fromConfig(config: AgentConfig): HttpMcpClient = HttpMcpClient(config.mcpServerUrl.trimEnd('/'))

        private fun defaultClient(): HttpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
    }
}
