package ru.course.roguelike.mcp.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse

@Serializable
data class GameHealthDto(
    val status: String,
    val service: String,
)

class GameServiceClient(
    private val baseUrl: String,
    private val http: HttpClient = defaultClient(),
) : GameSessionPort {
    suspend fun health(): GameHealthDto =
        http.get("$baseUrl/health").body()

    override suspend fun createSession(seed: Long?, twoLevel: Boolean, coopAgent: Boolean): GameSnapshot =
        http.post("$baseUrl/api/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    seed?.let { put("seed", it) }
                    put("twoLevel", twoLevel)
                    put("coopAgent", coopAgent)
                },
            )
        }.body()

    override suspend fun observe(sessionId: String): GameSnapshot =
        http.get("$baseUrl/api/v1/sessions/$sessionId/observe").body()

    override suspend fun applyAction(sessionId: String, action: String, actor: String): PlayerActionResponse =
        http.post("$baseUrl/api/v1/sessions/$sessionId/actions") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("action", action)
                    put("actor", actor)
                },
            )
        }.body()

    override suspend fun sync(sessionId: String, input: InputSyncRequest, actor: String): PlayerActionResponse =
        http.post("$baseUrl/api/v1/sessions/$sessionId/sync") {
            contentType(ContentType.Application.Json)
            setBody(input.copy(actor = actor))
        }.body()

    fun close() {
        http.close()
    }

    companion object {
        private fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
