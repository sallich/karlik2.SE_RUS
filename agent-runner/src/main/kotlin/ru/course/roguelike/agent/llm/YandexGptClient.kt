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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.AssistantMessage
import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.SystemMessage
import ru.course.roguelike.agent.ToolChoice
import ru.course.roguelike.agent.ToolResultMessage
import ru.course.roguelike.agent.UserMessage
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.agent.toYandex
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import kotlin.math.min

/**
 * YandexGPT Foundation Models API with retry/backoff and heuristic fallback.
 */
class YandexGptClient(
    private val config: AgentConfig,
    private val fallback: AgentDecisionClient,
    private val http: HttpClient = defaultClient(),
    private val model: String = "yandexgpt-lite/latest"
// "yandexgpt-5.1/latest
// "aliceai-llm/latest"
) : AgentDecisionClient {

    private val log = LoggerFactory.getLogger(YandexGptClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        actor: String,
    ): List<ToolCallDecision> {
        val yandexMessages = messages.map { it.toYandexMessage() }
        val yandexTools = availableTools.map { it.toYandexTool() }

        var delayMs = 500L
        repeat(config.retryAttempts) { attempt ->
            val decision = runCatching {
                val response = callYandex(yandexMessages, yandexTools)
                log.debug("YandexGPT response: {}", response)
                log.debug("Status: {}, message: {}", response.status, response.message)
                response.message?.toolCallList?.toolCalls?.forEach { call ->
                    log.debug("Tool call: {} with args {}", call.functionCall.name, call.functionCall.arguments)
                }
                when (response.status) {
                    ALTERNATIVE_STATUS_TOOL_CALL -> {
                        val toolCalls = response.message?.toolCallList?.toolCalls
                        if (!toolCalls.isNullOrEmpty()) {
                            val call = toolCalls.first()
                            val args = call.functionCall.arguments.toMutableMap()
                            args["sessionId"] = json.parseToJsonElement("\"$sessionId\"")
                            log.debug(
                                "Successfully got tool call: {}, {}",
                                call.functionCall.name,
                                call.functionCall.arguments,
                            )
                            ToolCallDecision(null, call.functionCall.name, call.functionCall.arguments)
                        } else {
                            null
                        }
                    }

                    else -> {
                        null
                    }
                }
            }.getOrElse { ex ->
                log.warn("YandexGPT attempt ${attempt + 1} failed: ${ex.message}")
                null
            }
            if (decision != null) return listOf(decision)
            if (attempt + 1 < config.retryAttempts) {
                kotlinx.coroutines.delay(delayMs)
                delayMs = min(delayMs * 2, 8_000L)
            }
        }
        return fallback.chooseTool(snapshot, sessionId, messages, availableTools, actor)
    }

    override suspend fun decide(
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        request: MobDecideRequest,
    ): MobDecideResponse {
        val yandexMessages = messages.map { it.toYandexMessage() }

        log.debug("openAiMessages -> {}", yandexMessages)
        val yandexTools = availableTools.map { it.toYandexTool() }

        var delayMs = 500L
        repeat(config.retryAttempts) { attempt ->
            val decision = runCatching {
                val response = callYandex(yandexMessages, yandexTools)
                log.debug("YandexGPT response: {}", response)
                log.debug("Status: {}, message: {}", response.status, response.message)
                response.message?.toolCallList?.toolCalls?.forEach { call ->
                    log.debug("Tool call: {} with args {}", call.functionCall.name, call.functionCall.arguments)
                }
                when (response.status) {
                    ALTERNATIVE_STATUS_TOOL_CALL -> {
                        val toolCalls = response.message?.toolCallList?.toolCalls
                        if (!toolCalls.isNullOrEmpty()) {
                            val call = toolCalls.first()
                            val args = call.functionCall.arguments.toMutableMap()
                            val intent = args["intent"]?.jsonPrimitive?.content?.lowercase()
                            if (intent != null) {
                                MobDecideResponse(intent, model)
                            } else {
                                log.warn("Некорректный intent")
                                null
                            }
                        } else {
                            null
                        }
                    }
                    else -> {
                        log.warn("Модель не вызвала инструмент, текст ответа: ${response.message?.text}")
                        null
                    }
                }
            }.getOrElse { ex ->
                log.warn("Попытка ${attempt + 1} завершилась ошибкой: ${ex.message}", ex)
                null
            }
            if (decision != null) return decision
            if (attempt + 1 < config.retryAttempts) {
                delay(delayMs)
                delayMs = min(delayMs * 2, 8_000L)
            }
        }
        log.warn("Все попытки вызова исчерпаны, переключение на fallback")
        return fallback.decide(messages, availableTools, request)
    }

    private suspend fun callYandex(
        messages: List<YandexMessage>,
        tools: List<YandexTool>,
        apiKey: String? = config.llmApiKey,
        folderId: String? = config.yandexFolderId,
    ): YandexAlternative {
        val body = YandexCompletionRequest(
            modelUri = "gpt://$folderId/$model",
            completionOptions = YandexCompletionOptions(temperature = 0.7, maxTokens = 512),
            messages = messages,
            tools = tools,
            toolChoice = ToolChoice(mode = "REQUIRED"),
        )

        val jsonBody = json.encodeToString(body)
        log.debug(jsonBody)

        val response = http.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key $apiKey")
            header("x-folder-id", folderId)
            setBody(body)
        }.body<YandexCompletionResponse>()
        return response.result.alternatives.first()
    }

    fun LLMMessage.toYandexMessage(): YandexMessage = when (this) {
        is SystemMessage -> YandexMessage(role = "system", text = text)

        is UserMessage -> YandexMessage(role = "user", text = text)

        is AssistantMessage -> YandexMessage(
            role = "assistant",
            text = text,
            toolCallList = toolCallList?.toYandex(),
        )

        is ToolResultMessage -> YandexMessage(role = "assistant", toolResultList = toolResultList.toYandex())
    }

    fun McpTool.toYandexTool(): YandexTool = YandexTool(
        function = YandexFunction(
            name = name,
            description = description,
            parameters = inputSchema,
        ),
    )

    /**
     * Функция для загрузки tools напрямую из mcp-contract.
     */
    @Suppress("unused")
    private fun loadToolsFromContract(): List<YandexTool> {
        val inputStream =
            javaClass.getResourceAsStream("/mcp-contract.json") ?: error("mcp-contract.json not found in resources")
        val contractJson = inputStream.bufferedReader().readText()
        val contract = json.decodeFromString<McpContract>(contractJson)
        return contract.tools.map { tool ->
            YandexTool(
                YandexFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.inputSchema,
                ),
            )
        }
    }

    companion object {

        @Suppress("unused")
        private const val SYSTEM_PROMPT = """"
Ты управляешь игроком в roguelike игре. У тебя есть MCP-инструменты:
- game_act: выполнить одно из действий:
    * move_north   — шаг наверх (увеличение координаты x)
    * move_south   — шаг вниз (уменьшение координаты x)
    * move_east    — шаг вправо (увеличение координаты y)
    * move_west    — шаг влево (уменьшение координаты y)
    * interact     — подобрать ключ или открыть выход
    * wait         — пропустить ход


Ты НЕ используешь turn_left/right, move_forward, game_sync, game_observe.

**Важно:** Ты получаешь карту в текстовом виде. Ось X — вертикальная (строки), ось Y — горизонтальная (столбцы). Твоя позиция — @.
**Стены (#) и лава (L) НЕПРОХОДИМЫ.** Ходить можно только по полу (.).

**Правила навигации (обязательные):**
1. **Перед тем как выбрать move_north/south/east/west, посмотри на карту: что находится в соседней клетке в этом направлении? Если там # или L — это направление ЗАПРЕЩЕНО.**
2. Если в желаемом направлении стена, выбери другое направление, где есть проход (.), даже если оно не ведёт прямо к ключу.
3. **Если ты упёрся в тупик (со всех сторон стены или лава), развернись (выбери противоположное направление) или посмотри на карту и придумай как обойти, сделай первый шаг к этому.**
4. **Запрещено повторять одно и то же действие более двух раз подряд, если позиция не изменилась или расстояние до цели не уменьшилось.**
5. Если за последние 3 хода расстояние до ближайшего ключа/выхода не уменьшилось — измени стратегию (попробуй другое направление, вернись к развилке).

**Алгоритм выбора действия (по шагам):**
1. Найди на карте все ключи (K) и выход (E). Определи, какой из них ближайший по МАНХЭТТЕНСКОМУ расстоянию (|x1-x2| + |y1-y2|), не по прямой.
2. **Мысленно построй путь из клеток '.' (игнорируя врагов), который ведёт к этой цели. Не пытайся идти сквозь стены.**
3. **Посмотри на первый шаг этого пути. Если в соседней клетке в этом направлении находится '.' (пол), то выбери это направление. Если там # или L, значит, путь неверен — нужно найти обход.**
4. **Чтобы найти обход, осмотрись вокруг: какие направления (север, юг, восток, запад) ведут в клетки '.'? Выбери то из них, которое в итоге позволит приблизиться к цели (например, если цель на севере, но север заблокирован, попробуй восток или запад, чтобы обойти стену).**
5. Если стоишь на ключе или выходе — используй interact.

**Пример рассуждения:**
"Я в (x=10, y=6). Ближайший ключ в (18,19). Прямой путь требует идти на север и восток. Проверяю северную клетку: на карте вижу # (стена). Значит, идти на север нельзя. Проверяю восточную клетку: там '.' (пол). Поэтому выбираю move_east, чтобы начать обход стены. План: сначала несколько шагов на восток, затем, когда появится проход на север, поверну."
Запрещено повторять одно и то же действие более двух раз подряд, если оно не привело к изменению позиции.
                """

        private const val ALTERNATIVE_STATUS_TOOL_CALL = "ALTERNATIVE_STATUS_TOOL_CALLS"

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
    val tools: List<YandexTool>,
    val toolChoice: ToolChoice,
)

@Serializable
private data class YandexCompletionOptions(
    val temperature: Double,
    val maxTokens: Int,
)

@Serializable
data class YandexMessage(
    val role: String,
    val text: String? = null,
    val toolCallList: YandexToolCallList? = null,
    val toolResultList: YandexToolResultList? = null,
)

@Serializable
data class YandexToolCallList(
    val toolCalls: List<YandexToolCall>,
)

@Serializable
data class YandexToolCall(
    val functionCall: YandexFunctionCall,
)

@Serializable
data class YandexFunctionCall(
    val name: String,
    val arguments: JsonObject,
)

@Serializable
private data class YandexCompletionResponse(
    val result: YandexResult,
)

@Serializable
private data class YandexResult(
    val alternatives: List<YandexAlternative>,
)

@Serializable
private data class YandexAlternative(
    val message: YandexMessage? = null,
    val status: String,
)

@Serializable
data class YandexTool(
    val function: YandexFunction,
)

@Serializable
data class YandexFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class YandexToolResultList(
    val toolResults: List<YandexToolResult>,
)

@Serializable
data class YandexToolResult(
    val functionResult: YandexFunctionResult,
)

@Serializable
data class YandexFunctionResult(
    val name: String,
    val content: String,
)

@Serializable
private data class McpContract(
    val tools: List<McpTool>,
)
