package ru.course.roguelike.agent.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import kotlin.math.min

/**
 * YandexGPT Foundation Models API with retry/backoff and heuristic fallback.
 */
class YandexGptClient(
    private val config: AgentConfig,
    private val fallback: AgentDecisionClient,
    private val http: HttpClient = defaultClient(),
) : AgentDecisionClient {
    private val log = LoggerFactory.getLogger(YandexGptClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val planner = KeyHuntPlanner()

    override suspend fun chooseTool(snapshot: GameSnapshot, sessionId: String, actor: String): ToolCallDecision {
        val apiKey = config.llmApiKey
        val folderId = config.yandexFolderId
        if (apiKey.isNullOrBlank() || folderId.isNullOrBlank()) {
            log.warn("YandexGPT credentials missing, using heuristic fallback")
            return fallback.chooseTool(snapshot, sessionId, actor)
        }

        var delayMs = 500L
        repeat(config.retryAttempts) { attempt ->
            val decision = runCatching {
                val prompt = buildPrompt(snapshot)
                val responseText = callYandex(apiKey, folderId, prompt)
                parseToolDecision(responseText, sessionId) ?: planner.plan(snapshot, sessionId, actor)
            }.getOrElse { ex ->
                log.warn("YandexGPT attempt ${attempt + 1} failed: ${ex.message}")
                null
            }
            if (decision != null) return decision
            if (attempt + 1 < config.retryAttempts) {
                kotlinx.coroutines.delay(delayMs)
                delayMs = min(delayMs * 2, 8_000L)
            }
        }
        return fallback.chooseTool(snapshot, sessionId, actor)
    }

    private suspend fun callYandex(apiKey: String, folderId: String, prompt: String): String {
        val body = YandexCompletionRequest(
            modelUri = "gpt://$folderId/yandexgpt-lite/latest",
            completionOptions = YandexCompletionOptions(temperature = 0.2, maxTokens = 256),
            messages = listOf(
                YandexMessage(role = "system", text = SYSTEM_PROMPT),
                YandexMessage(role = "user", text = prompt),
            ),
        )
        val response = http.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key $apiKey")
            header("x-folder-id", folderId)
            setBody(body)
        }.body<YandexCompletionResponse>()
        return response.result.alternatives.firstOrNull()?.message?.text ?: ""
    }

    private fun buildPrompt(snapshot: GameSnapshot): String =
        """
        phase=${snapshot.phase}
        hp=${snapshot.player.hp}/${snapshot.player.maxHp}
        keys=${snapshot.keysCollected}/${snapshot.keysRequired}
        player=(${snapshot.player.pose.x}, ${snapshot.player.pose.y})
        keyCount=${snapshot.keyPickups.size}
        exitGate=${snapshot.exitGate}
        Reply JSON only: {"tool":"game_act|game_sync","arguments":{...}}
        """.trimIndent()

    private fun parseToolDecision(text: String, sessionId: String): ToolCallDecision? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        val tool = obj["tool"]?.jsonPrimitive?.content ?: return null
        val args = obj["arguments"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        if (!args.containsKey("sessionId")) {
            args["sessionId"] = json.parseToJsonElement("\"$sessionId\"")
        }
        return ToolCallDecision(tool, args.mapValues { it.value })
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a roguelike agent. Collect all keys then interact on exit gate. " +
                "Output one JSON tool call using game_act (move_forward, turn_left, interact) " +
                "or game_sync (yawDelta, deltaMs)."

        private fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
}

@Serializable
private data class YandexCompletionRequest(
    val modelUri: String,
    val completionOptions: YandexCompletionOptions,
    val messages: List<YandexMessage>,
)

@Serializable
private data class YandexCompletionOptions(
    val temperature: Double,
    val maxTokens: Int,
)

@Serializable
private data class YandexMessage(val role: String, val text: String)

@Serializable
private data class YandexCompletionResponse(val result: YandexResult)

@Serializable
private data class YandexResult(val alternatives: List<YandexAlternative>)

@Serializable
private data class YandexAlternative(val message: YandexMessage)
