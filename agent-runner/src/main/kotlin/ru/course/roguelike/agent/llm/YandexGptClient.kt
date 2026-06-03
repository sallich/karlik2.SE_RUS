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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.PlayerPose
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

    private val sessionHistory = mutableMapOf<String, MutableList<String>>()
    private val MCPTools = loadToolsFromContract()
    private val maxHistorySize = 7
    private var agentPos = PlayerPose(0.0f, 0.0f, 0.0f)

    private val lastActionNames = mutableListOf<String>()
    private val maxLastActions = 3

    fun recordActionName(actionName: String) {
        lastActionNames.add(actionName)
        while (lastActionNames.size > maxLastActions) lastActionNames.removeFirst()
    }

    fun getToolsName(): List<String> {
        return MCPTools.map { it.function.name }
    }

    fun getX(): Float {
        return agentPos.x
    }

    fun getY(): Float {
        return agentPos.y
    }

    fun getYaw(): Float {
        return agentPos.yaw
    }

    fun updatePlayerPosition(x: Float, y: Float, yaw: Float) {
        agentPos = PlayerPose(x, y, yaw)
    }

    override suspend fun chooseTool(
        snapshot: GameSnapshot,
        sessionId: String,
        actor: String
    ): ToolCallDecision {
        val apiKey = config.llmApiKey
        val folderId = config.yandexFolderId
        if (apiKey.isNullOrBlank() || folderId.isNullOrBlank()) {
            log.warn("YandexGPT credentials missing, using heuristic fallback")
            return fallback.chooseTool(snapshot, sessionId, actor)
        }
        if (agentPos.x == 0.0F && agentPos.y == 0.0F) {
            val x = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
            val y = snapshot.agent?.pose?.y ?: snapshot.player.pose.y
            val yaw = snapshot.agent?.pose?.yaw ?: snapshot.player.pose.yaw
            updatePlayerPosition(x, y, yaw)
        }
        sessionHistory.getOrPut(sessionId) { mutableListOf() }
        val userPromt = buildPrompt(snapshot)
        log.debug(userPromt)
        val userMessage = YandexMessage(role = "user", text = userPromt)
        val systemMessage = YandexMessage(role = "system", text = SYSTEM_PROMPT)
        val fullMessages = listOf(systemMessage, userMessage)


        var delayMs = 500L
        repeat(config.retryAttempts) { attempt ->
            val decision = runCatching {
                val response = callYandex(apiKey, folderId, fullMessages, MCPTools)
                log.debug(response.toString())
                when (response.status) {
                    ALTERNATIVE_STATUS_TOOL_CALL -> {
                        val toolCalls = response.message?.toolCallList?.toolCalls
                        if (!toolCalls.isNullOrEmpty()) {
                            val call = toolCalls.first()
                            val args = call.functionCall.arguments.toMutableMap()
                            args["sessionId"] = json.parseToJsonElement("\"$sessionId\"")
                            log.debug("Successfully got tool call: {}, {}", call.functionCall.name, args.values)
                            if (args.containsKey("explanation")) {
                                log.debug("explanation got: {}", args.values)
                                args.remove("explanation")
                            }
                            ToolCallDecision(call.functionCall.name, args.mapValues { it.value })
                        } else {
                            null
                        }
                    }

                    else -> {
                        val text = response.message?.text ?: ""
                        log.debug("YandexGPT response: $text")
                        parseToolDecision(text, sessionId) ?: planner.plan(snapshot, sessionId, actor)
                    }
                }
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

    private suspend fun callYandex(
        apiKey: String,
        folderId: String,
        messages: List<YandexMessage>,
        tools: List<YandexTool>,
    ): YandexAlternative {
        val model = "yandexgpt-5.1/latest"
        val model_light = "yandexgpt-lite/latest"
        val model_alice = "aliceai-llm/latest"
        val body = YandexCompletionRequest(
            modelUri = "gpt://$folderId/$model_alice",
            completionOptions = YandexCompletionOptions(temperature = 0.3, maxTokens = 256),
            messages = messages,
            tools = tools,
        )
        val response = http.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key $apiKey")
            header("x-folder-id", folderId)
            setBody(body)
        }.body<YandexCompletionResponse>()
        return response.result.alternatives.first()
    }

    private fun buildPrompt(snapshot: GameSnapshot): String {
        val playerX = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
        val playerY = snapshot.agent?.pose?.y ?: snapshot.player.pose.y
        val playerYaw = snapshot.agent?.pose?.yaw ?: snapshot.player.pose.yaw
        val playerHP = snapshot.agent?.hp ?: snapshot.player.hp
        val playerMaxHP = snapshot.agent?.maxHp ?: snapshot.player.maxHp

        val keys = snapshot.keyPickups.joinToString(prefix = "[", postfix = "]") { key ->
            val x = key.x.toInt()
            val y = key.y.toInt()
            "($x, $y)"
        }

        val localMap = formatFullMap(snapshot)

        val history = getHistoryString(snapshot.sessionId)

        return """
        Фаза: ${snapshot.phase}
        Здоровье: $playerHP/$playerMaxHP
        Ключи: ${snapshot.keysCollected}/${snapshot.keysRequired} (собрано/необходимо)
        Позиция игрока: x: $playerX, y: $playerY
        Направление взгляда: $playerYaw
        Список координат оставшихся ключей: ${keys}$
        
        Выход: ${snapshot.exitGate}
        
$history
       
        Полная карта игры (@=ты, K=ключ, E=выход, #=стена, .=пол), ходить можно только по полу:
        $localMap
        
        Посмотри на карту и историю. Реши какое действие сделать следующим, чтобы приблизиться к концу игры. Вертикальная ось это x, а горизонтальная это y. 
        """.trimIndent()
    }

    fun getHistoryString(sessionId: String): String {
        val history = sessionHistory[sessionId]
        return if (history.isNullOrEmpty()) {
            "\nИстория действий пуста."
        } else {
            "\nПоследние действия:\n" + history.joinToString("\n")
        }
    }

    fun addToHistory(sessionId: String, description: String) {
        val history = sessionHistory.getOrPut(sessionId) { mutableListOf() }
        history.add(description)
        while (history.size > maxHistorySize) {
            history.removeFirst()
        }
        log.debug("History updated: ${description}")
    }

    fun formatFullMap(snapshot: GameSnapshot): String {
        val w = snapshot.width
        val h = snapshot.height
        val tiles = snapshot.tiles
        val playerX = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
        val playerY = snapshot.agent?.pose?.y ?: snapshot.player.pose.y
        // Строим исходную сетку символов без поворота
        val originalGrid = Array(h) { y ->
            CharArray(w) { x ->
                val tileType = tiles[y * w + x]
                when {
                    x == playerX.toInt() && y == playerY.toInt() -> '@'
                    snapshot.keyPickups.any { it.x.toInt() == x && it.y.toInt() == y } -> 'K'
                    snapshot.exitGate?.let { it.x == x && it.y == y } == true -> 'E'
                    tileType.damaging -> 'L'
                    tileType.walkable -> '.'
                    else -> '#'
                }
            }
        }

        val rotatedGrid = rotateRight(originalGrid)

        val newHeight = rotatedGrid.size
        val newWidth = rotatedGrid[0].size

        val sb = StringBuilder()

        sb.append("     ")
        for (x in 0 until newWidth) {
            if (x % 10 == 0) sb.append("|")
            sb.append(x % 10)
        }
        sb.append('\n')
        sb.append("     ")
        repeat(newWidth) { sb.append('-') }
        sb.append('\n')

        for (y in 0 until newHeight) {
            sb.append(String.format("%3d |", newHeight - 1 - y))
            sb.append(rotatedGrid[y])
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun rotateRight(grid: Array<CharArray>): Array<CharArray> {
        val height = grid.size
        val width = grid[0].size
        val rotated = Array(width) { CharArray(height) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[width - 1 - x][y] = grid[y][x]
            }
        }
        return rotated
    }

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

    private fun loadToolsFromContract(): List<YandexTool> {
        val inputStream = javaClass.getResourceAsStream("/mcp-contract.json")
            ?: error("mcp-contract.json not found in resources")
        val contractJson = inputStream.bufferedReader().readText()
        val contract = json.decodeFromString<McpContract>(contractJson)
        return contract.tools.map { tool ->
            YandexTool(
                YandexFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.inputSchema
                )
            )
        }
    }


    companion object {
        private const val SYSTEM_PROMPT =
            """"
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
    val tools: List<YandexTool>
)

@Serializable
private data class YandexCompletionOptions(
    val temperature: Double,
    val maxTokens: Int,
)

@Serializable
private data class YandexMessage(
    val role: String,
    val text: String? = null,
    val toolCallList: YandexToolCallList? = null,
)

@Serializable
private data class YandexToolCallList(
    val toolCalls: List<YandexToolCall>
)

@Serializable
private data class YandexToolCall(
    val functionCall: YandexFunctionCall
)

@Serializable
private data class YandexFunctionCall(
    val name: String,
    val arguments: JsonObject
)

@Serializable
private data class YandexCompletionResponse(val result: YandexResult)

@Serializable
private data class YandexResult(val alternatives: List<YandexAlternative>)

@Serializable
private data class YandexAlternative(
    val message: YandexMessage? = null,
    val status: String
)

@Serializable
private data class YandexTool(
    val function: YandexFunction
)

@Serializable
private data class YandexFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)


@Serializable
private data class McpContract(
    val tools: List<McpTool>
)

@Serializable
private data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)