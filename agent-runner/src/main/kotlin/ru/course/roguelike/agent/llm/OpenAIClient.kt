package ru.course.roguelike.agent.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.util.*
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
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.TileType
import kotlin.math.min

/**
 * Клиент OpenAI API совместимый (использовался для Qwen 3 через Ollama локально) с поддержкой вызова инструментов.
 * Использует API /api/chat/completions Ollama.
 */
class OpenAIClient(
    private val config: AgentConfig,
    private val fallback: AgentDecisionClient,
    private val http: HttpClient = defaultClient(),
    private val modelName: String = config.ollamaModelUrl,
    private val ollamaUrl: String = "http://host.docker.internal:11434"
) : AgentDecisionClient {

    private val log = LoggerFactory.getLogger(OpenAIClient::class.java)
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
        tools: List<OpenAiTool>
    ): OpenAiChatResponse {
        val request = OpenAiChatRequest(
            model = modelName,
            messages = messages,
            tools = tools,
            toolChoice = "required",
            stream = false
        )
        return http.post("$ollamaUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<OpenAiChatResponse>()
    }

    /**
     * Строит список сообщений в формате OpenAI.
     * Добавляет системный промпт с картой и правилами.
     */
    private fun buildOpenAiMessages(
        history: List<LLMMessage>,
        snapshot: GameSnapshot
    ): List<OpenAiMessage> {
        val systemPrompt = buildSystemPrompt(snapshot)
        val result = mutableListOf<OpenAiMessage>()

        // Системное сообщение
        result.add(OpenAiMessage(role = "system", content = systemPrompt))

        for (msg in history) {
            when (msg) {
                is SystemMessage -> continue // уже добавили свой
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
                    // Каждый результат инструмента → сообщение role = "tool"
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

    /**
     * Формирует системный промпт с картой и инструкциями.
     * Можно скопировать из YandexGptClient или адаптировать.
     */
    private fun buildSystemPrompt(snapshot: GameSnapshot): String {
        val playerX = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
        val playerY = snapshot.agent?.pose?.y ?: snapshot.player.pose.y
        val playerYaw = snapshot.agent?.pose?.yaw ?: snapshot.player.pose.yaw
        val playerHP = snapshot.agent?.hp ?: snapshot.player.hp
        val playerMaxHP = snapshot.agent?.maxHp ?: snapshot.player.maxHp

        val keys = snapshot.keyPickups.joinToString(prefix = "[", postfix = "]") { key ->
            "(${key.x.toInt()}, ${key.y.toInt()})"
        }

        val keyCollectedMessage =
            if (snapshot.keysCollected == snapshot.keysRequired) "Все ключи собраны, надо идти к выходу." else ""

        val localMap = getLocalMap(snapshot)

        return """
            Ты — агент в roguelike игре. Твоя задача — собрать все ключи и встать на выход.
            Карта игры представляет из себя лабиринт с комнатами.
            Тебе предстоит передвигаться через коридоры между комнатами и достигнуть выхода.
        Текущая сессия: ${snapshot.sessionId}
        Доступен ТОЛЬКО один инструмент: game_act.
            * move_north   — шаг наверх (увеличение координаты x)
            * move_south   — шаг вниз (уменьшение координаты x)
            * move_east    — шаг вправо (увеличение координаты y)
            * move_west    — шаг влево (уменьшение координаты y)
            * interact     — подобрать ключ или открыть выход
            * wait         — пропустить ход
        Параметр action может быть: move_north, move_south, move_east, move_west, interact, wait.

        ПРАВИЛА:
        - ЗАПРЕЩЕНО писать любой текст. Не рассуждай, не объясняй, не описывай.
        - Твой ответ должен быть ТОЛЬКО вызовом инструмента game_act.
        - Пример правильного ответа: вызов game_act с {"action": "interact"}.

**Если ты получил сообщение "Внимание: вы повторяете одни и те же действия или стоите на месте. Измените стратегию." – ты НЕМЕДЛЕННО должен выбрать действие, отличное от предыдущих четырёх. Например, если ты 4 раза подряд двигался на восток, выбери move_south, move_north или move_west.**
        Никаких слов. Только вызов инструмента.
Ты управляешь игроком в roguelike игре. У тебя есть MCP-инструменты (game_act и другие). Всегда вызывай game_act с нужным действием.

Фаза: ${snapshot.phase}
Здоровье: $playerHP/$playerMaxHP
Ключи: ${snapshot.keysCollected}/${snapshot.keysRequired}
Позиция игрока: x=$playerX, y=$playerY
Взгляд: yaw = $playerYaw
Список оставшихся ключей: $keys
Выход: ${snapshot.exitGate}

Карта (x - вертикаль, y - горизонталь, @ = ты, K = ключ, E = выход, # = стена, . = пол, L = лава):
$localMap

$keyCollectedMessage

Правила:
- Не пытайся пройти сквозь стену (#) или лаву (L).
- Если упёрся в тупик — выбирай другое направление.
- Сначала проверяй соседнюю клетку в выбранном направлении: если там стена или лава — не ходи туда.
- Используй interact только если стоишь на ключе или выходе.
- ВАЖНО: Всегда вызывай game_act с действием. Никогда не отвечай текстом без вызова инструмента.
        """.trimIndent()
    }

    private fun getMobChar(x: Int, y: Int, snapshot: GameSnapshot): Char? {
        return snapshot.mobs.firstOrNull { it.x.toInt() == x && it.y.toInt() == y }
            ?.let { mob ->
                when (mob.kind) {
                    MobKind.MELEE -> 'M'
                    MobKind.RANGED -> 'R'
                    MobKind.LLM_GUARD -> 'G'
                    else -> '?'
                }
            }
    }

    private fun getLocalCellChar(
        x: Int,
        y: Int,
        playerX: Int,
        playerY: Int,
        snapshot: GameSnapshot
    ): Char {
        if (x < 0 || x >= snapshot.width || y < 0 || y >= snapshot.height) return '#'
        if (x == playerX && y == playerY) return '@'

        getMobChar(x, y, snapshot)?.let { return it }

        if (snapshot.keyPickups.any { it.x.toInt() == x && it.y.toInt() == y }) return 'K'

        if (snapshot.exitGate?.x == x && snapshot.exitGate?.y == y) return 'E'

        val tileType = snapshot.tiles[y * snapshot.width + x]
        return when {
            tileType.damaging -> 'L'
            tileType.walkable -> '.'
            else -> '#'
        }
    }

    /**
     * Возвращает текстовое представление карты размером radius*2+1 x radius*2+1
     * с центром в позиции игрока.
     * @param snapshot текущий снапшот
     * @param radius радиус обзора (1 => 3x3, 2 => 5x5)
     */
    fun getLocalMap(snapshot: GameSnapshot, radius: Int = 4): String {
        val playerX = (snapshot.agent?.pose?.x ?: snapshot.player.pose.x).toInt()
        val playerY = (snapshot.agent?.pose?.y ?: snapshot.player.pose.y).toInt()

        val sb = StringBuilder()
        sb.append("Локальная карта ${radius * 2 + 1}x${radius * 2 + 1} (центр = игрок @):\n")
        // строки сверху вниз (x уменьшается к северу)
        for (dy in -radius..radius) {
            sb.append("  ")
            for (dx in -radius..radius) {
                val x = playerX + dx
                val y = playerY + dy
                val ch = getLocalCellChar(x, y, playerX, playerY, snapshot)
                sb.append(ch)
                sb.append(' ')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun getCellChar(
        x: Int,
        y: Int,
        snapshot: GameSnapshot,
        playerX: Int,
        playerY: Int
    ): Char {
        // Игрок
        if (x == playerX && y == playerY) return '@'
        // Ключ
        if (snapshot.keyPickups.any { it.x.toInt() == x && it.y.toInt() == y }) return 'K'
        // Выход
        if (snapshot.exitGate?.x == x && snapshot.exitGate?.y == y) return 'E'

        val tileType = snapshot.tiles[y * snapshot.width + x]
        return when {
            tileType.damaging -> 'L'
            tileType.walkable -> '.'
            else -> '#'
        }
    }

    @Suppress("unused")
    private fun formatMap(snapshot: GameSnapshot): String {
        val w = snapshot.width
        val h = snapshot.height
        val tiles = snapshot.tiles
        val playerX = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
        val playerY = snapshot.agent?.pose?.y ?: snapshot.player.pose.y

        val grid = Array(h) { y ->
            CharArray(w) { x ->
                getCellChar(x, y, snapshot, playerX.toInt(), playerY.toInt())
            }
        }
        val rotated = rotateRight(grid)
        val sb = StringBuilder()
        sb.append("     ")
        for (x in rotated[0].indices) sb.append(x % 10)
        sb.append("\n     ")
        repeat(rotated[0].size) { sb.append('-') }
        sb.append('\n')
        for (y in rotated.indices) {
            sb.append(String.format(Locale.US,"%3d |", rotated.size - 1 - y))
            sb.append(rotated[y])
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun rotateRight(grid: Array<CharArray>): Array<CharArray> {
        val h = grid.size
        val w = grid[0].size
        val rotated = Array(w) { CharArray(h) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                rotated[w - 1 - x][y] = grid[y][x]
            }
        }
        return rotated
    }

    private fun McpTool.toOpenAiTool(): OpenAiTool {
        return OpenAiTool(
            type = "function", function = OpenAiFunction(
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
