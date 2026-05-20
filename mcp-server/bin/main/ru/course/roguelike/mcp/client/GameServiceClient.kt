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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class GameHealthDto(
    val status: String,
    val service: String,
)

@Serializable
data class GameSessionDto(
    val sessionId: String,
    val seed: Long,
    val phase: String,
    val message: String,
)

class GameServiceClient(
    private val baseUrl: String,
    private val http: HttpClient = defaultClient(),
) {
    suspend fun health(): GameHealthDto =
        http.get("$baseUrl/health").body()

    suspend fun createSession(seed: Long?): GameSessionDto =
        http.post("$baseUrl/api/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { seed?.let { put("seed", it) } })
        }.body()

    suspend fun applyAction(sessionId: String, action: String): JsonElement =
        http.post("$baseUrl/api/v1/sessions/$sessionId/actions") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("action", action) })
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
