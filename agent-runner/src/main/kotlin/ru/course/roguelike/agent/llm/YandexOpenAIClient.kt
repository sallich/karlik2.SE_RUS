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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.AssistantMessage
import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.OpenAiChatRequest
import ru.course.roguelike.agent.OpenAiChatResponse
import ru.course.roguelike.agent.OpenAiFunction
import ru.course.roguelike.agent.OpenAiFunctionCall
import ru.course.roguelike.agent.OpenAiMessage
import ru.course.roguelike.agent.OpenAiTool
import ru.course.roguelike.agent.OpenAiToolCall
import ru.course.roguelike.agent.SystemMessage
import ru.course.roguelike.agent.ToolResultMessage
import ru.course.roguelike.agent.UserMessage
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.llm.PromptBuilder.buildSystemPrompt
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import kotlin.collections.set
import kotlin.math.min

/**
 * Клиент OpenAI API совместимый (использовался для Qwen 3 через Ollama локально) с поддержкой вызова инструментов.
 * Использует API /api/chat/completions Ollama.
 */
class YandexOpenAIClient(
    private val config: AgentConfig,
    private val fallback: AgentDecisionClient,
    private val http: HttpClient = defaultClient(),
    private val modelName: String = "qwen3.6-35b-a3b/latest",
    private val baseUrl: String = "https://ai.api.cloud.yandex.net/v1/chat/completions"
) : AgentDecisionClient {

    private val log = LoggerFactory.getLogger(YandexOpenAIClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        actor: String
    ): List<ToolCallDecision> {
        val openAiMessages = buildOpenAiMessages(messages, snapshot)
        log.debug("openAiMessages -> {}", openAiMessages)
        val openAiTools = availableTools.map { it.toOpenAiTool() }

        var delayMs = 500L
        repeat(config.retryAttempts) { attempt ->
            val decision = runCatching {
                val response = callOpenAi(openAiMessages, openAiTools)
                log.debug("OpenAI‑совместимый ответ: {}", response)

                val choice = response.choices.firstOrNull()
                val assistantMessage = choice?.message ?: return@runCatching null
                val toolCalls = assistantMessage.toolCalls

                if (!toolCalls.isNullOrEmpty()) {
                    val decisions = toolCalls.map { call ->
                        val argsString = call.function.arguments
                        val argsJson = json.parseToJsonElement(argsString).jsonObject
                        val enrichedArgs = argsJson.toMutableMap()
                        enrichedArgs["sessionId"] = json.parseToJsonElement("\"$sessionId\"")
                        log.debug("Вызван инструмент: {} с аргументами {}", call.function.name, argsString)
                        ToolCallDecision(call.id, call.function.name, enrichedArgs)
                    }
                    decisions
                } else {
                    log.warn("Модель не вызвала инструмент, текст ответа: ${assistantMessage.content}")
                    null
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
        return fallback.chooseTool(snapshot, sessionId, messages, availableTools, actor)
    }

    override suspend fun decide(
        messages: List<LLMMessage>,
        availableTools: List<McpTool>,
        request: MobDecideRequest
    ): MobDecideResponse {
        val openAiMessages = buildOpenAiMessagesForMob(messages)
        log.debug("openAiMessages -> {}", openAiMessages)
        val openAiTools = availableTools.map { it.toOpenAiTool() }

        var delayMs = 500L
        repeat(config.retryAttempts) { attempt ->
            val decision = runCatching {
                val response = callOpenAi(openAiMessages, openAiTools)
                log.debug("OpenAI‑совместимый ответ: {}", response)

                val choice = response.choices.firstOrNull()
                val assistantMessage = choice?.message ?: return@runCatching null
                val toolCalls = assistantMessage.toolCalls

                if (!toolCalls.isNullOrEmpty()) {
                    val call = toolCalls.first()
                    val argsString = call.function.arguments
                    val argsJson = json.parseToJsonElement(argsString).jsonObject
                    val intent = argsJson["intent"]?.jsonPrimitive?.content?.lowercase()
                    if (intent != null) {
                        MobDecideResponse(intent, modelName)
                    } else {
                        log.warn("Некорректный intent")
                        null
                    }
                } else {
                    log.warn("Модель не вызвала инструмент, текст ответа: ${assistantMessage.content}")
                    null
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

    private suspend fun callOpenAi(
        messages: List<OpenAiMessage>,
        tools: List<OpenAiTool>,
        apiKey: String? = config.llmApiKey,
        folderId: String? = config.yandexFolderId
    ): OpenAiChatResponse {
        val request = OpenAiChatRequest(
            model = "gpt://$folderId/$modelName",
            messages = messages,
            tools = tools,
            toolChoice = "required",
            stream = false
        )
        val responseText = http.post(baseUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key $apiKey")
            header("Project", folderId)
            setBody(request)
        }.body<String>()
        log.debug("Raw response from Yandex: {}", responseText)
        val response = json.decodeFromString<OpenAiChatResponse>(responseText)
        return response
    }

    /**
     * Строит список сообщений в формате OpenAI.
     * Добавляет системный промпт с картой и правилами.
     */
    private fun buildOpenAiMessages(
        history: List<LLMMessage>,
        snapshot: GameSnapshot
    ): List<OpenAiMessage> {
        val userPrompt = "Пройди игру."
        val systemPrompt = buildSystemPrompt(snapshot)
        val result = mutableListOf<OpenAiMessage>()

        result.add(OpenAiMessage(role = "system", content = systemPrompt))
        result.add(OpenAiMessage(role = "user", content = userPrompt))

        val localHistory = history.takeLast(20)
        for (msg in localHistory) {
            when (msg) {
                is SystemMessage -> continue
                is UserMessage -> {
                    result.add(OpenAiMessage(role = "user", content = msg.text))
                }

                is AssistantMessage -> {
                    if (msg.toolCallList != null && msg.toolCallList?.toolCalls?.isNotEmpty() == true) {
                        val toolCalls = msg.toolCallList?.toolCalls?.map { call ->
                            OpenAiToolCall(
                                id = call.id ?: "call_${System.currentTimeMillis()}",
                                index = 0,
                                type = "function",
                                function = OpenAiFunctionCall(
                                    name = call.functionCall.name,
                                    arguments = call.functionCall.arguments.toString()
                                )
                            )
                        }
                        result.add(
                            OpenAiMessage(
                                role = "assistant",
                                content = msg.text,
                                toolCalls = toolCalls
                            )
                        )
                    } else {
                        result.add(OpenAiMessage(role = "assistant", content = msg.text))
                    }
                }

                is ToolResultMessage -> {
                    // Каждый результат инструмента -> сообщение role = "tool"
                    msg.toolResultList.toolResults.forEach { toolResult ->
                        result.add(
                            OpenAiMessage(
                                role = "tool",
                                content = toolResult.functionResult.content,
                                toolCallId = toolResult.functionResult.toolCallId
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun buildOpenAiMessagesForMob(history: List<LLMMessage>): List<OpenAiMessage> {
        val result = mutableListOf<OpenAiMessage>()
        for (msg in history) {
            when (msg) {
                is UserMessage -> {
                    result.add(OpenAiMessage(role = "user", content = msg.text))
                }

                else -> {
                    result.add(OpenAiMessage(role = "assistant", content = msg.text))
                }
            }
        }
        return result
    }

    private fun McpTool.toOpenAiTool(): OpenAiTool {
        return OpenAiTool(
            type = "function",
            function = OpenAiFunction(
                name = this.name,
                description = this.description,
                parameters = this.inputSchema
            )
        )
    }

    companion object {
        private fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
