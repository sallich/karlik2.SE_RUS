package ru.course.roguelike.client.net

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse

class GameApiClient(
    private val baseUrl: String,
    private val http: HttpClient = createHttp(),
) {
    suspend fun createSession(seed: Long? = null, twoLevel: Boolean = false, coopAgent: Boolean = true): GameSnapshot =
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

    suspend fun observe(sessionId: String): GameSnapshot =
        http.get("$baseUrl/api/v1/sessions/$sessionId/observe").body()

    suspend fun sync(sessionId: String, input: InputSyncRequest): PlayerActionResponse =
        http.post("$baseUrl/api/v1/sessions/$sessionId/sync") {
            contentType(ContentType.Application.Json)
            setBody(input)
        }.body()

    suspend fun move(sessionId: String, action: String): PlayerActionResponse =
        http.post("$baseUrl/api/v1/sessions/$sessionId/actions") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("action", action) })
        }.body()

    fun close() {
        http.close()
    }

    companion object {
        private val jsonCodec = Json { ignoreUnknownKeys = true }

        private fun createHttp(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonCodec)
            }
        }
    }
}
