package ru.course.roguelike.game.infrastructure.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.course.roguelike.game.domain.ai.MobDecisionContext
import ru.course.roguelike.game.domain.ai.MobIntent

@Serializable
private data class MobDecideRequestDto(
    val mobId: Long,
    val mobX: Float,
    val mobY: Float,
    val playerX: Float,
    val playerY: Float,
    val distance: Float,
    val playerHp: Int,
)

@Serializable
private data class MobDecideResponseDto(
    val intent: String,
)

class AgentRunnerMobClient(
    private val baseUrl: String,
    private val http: HttpClient = defaultClient(),
) {
    suspend fun decide(context: MobDecisionContext): MobIntent? {
        return try {
            val response = http.post("$baseUrl/api/v1/mob/decide") {
                contentType(ContentType.Application.Json)
                setBody(
                    MobDecideRequestDto(
                        mobId = context.mob.id,
                        mobX = context.mob.x,
                        mobY = context.mob.y,
                        playerX = context.playerX,
                        playerY = context.playerY,
                        distance = context.distanceToPlayer,
                        playerHp = context.playerHp,
                    ),
                )
            }.body<MobDecideResponseDto>()
            parseIntent(response.intent)
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        http.close()
    }

    companion object {
        fun fromEnvironment(): AgentRunnerMobClient =
            AgentRunnerMobClient(
                System.getenv("AGENT_RUNNER_URL")?.trimEnd('/') ?: "http://localhost:8082",
            )

        private fun defaultClient(): HttpClient {
            val timeoutMs = System.getenv("MOB_DECIDE_TIMEOUT_MS")?.toLongOrNull() ?: 60_000L
            return HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = timeoutMs
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = timeoutMs
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        }

        private fun parseIntent(raw: String): MobIntent? = when (raw.lowercase()) {
            "chase" -> MobIntent.ChasePlayer
            "kite" -> MobIntent.KitePlayer
            "shoot" -> MobIntent.ShootPlayer
            "attack" -> MobIntent.AttackPlayer
            "idle" -> MobIntent.Idle
            "strafe" -> MobIntent.StrafePlayer
            else -> null
        }
    }
}
