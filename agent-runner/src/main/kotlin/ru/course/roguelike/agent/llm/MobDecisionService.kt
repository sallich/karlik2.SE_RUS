package ru.course.roguelike.agent.llm

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.MobDecideRequest
import ru.course.roguelike.agent.MobDecideResponse
import ru.course.roguelike.agent.UserMessage
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.shared.mcp.McpTool

private fun AgentConfig.forMobDecide(): AgentConfig = copy(
    llmRequestTimeoutMs = mobLlmRequestTimeoutMs,
    ollamaNumPredict = mobOllamaNumPredict,
    ollamaNumCtx = mobOllamaNumCtx,
)

class MobDecisionService(
    config: AgentConfig,
    private val llm: AgentDecisionClient = LlmClientFactory().create(config.forMobDecide(), HeuristicDecisionClient()),
    private val fallback: HeuristicDecisionClient = HeuristicDecisionClient(),
) {
    private val config = config.forMobDecide()
    private val budget = config.maxMobToolCalls
    private var steps = 0
    private val log = LoggerFactory.getLogger(MobDecisionService::class.java)

    val mobTool = McpTool(
        name = "mob_action",
        description = "Выбор действия моба в бою",
        inputSchema = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "intent",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                buildJsonArray {
                                    add("chase")
                                    add("shoot")
                                    add("kite")
                                    add("idle")
                                },
                            )
                            put(
                                "description",
                                "Действие моба: chase - преследовать," +
                                    " shoot - стрелять, kite - отступать," +
                                    " idle - бездействовать",
                            )
                        },
                    )
                },
            )
            put(
                "required",
                buildJsonArray {
                    add("intent")
                },
            )
            put("additionalProperties", false)
        },
    )

    private val tools = listOf(mobTool)

    suspend fun decide(request: MobDecideRequest): MobDecideResponse {
        if (config.llmProvider == "heuristic" || steps >= budget) {
            log.debug("Бюджет закончился - переход на эвристический.")
            return fallback.decide(emptyList(), tools, request)
        }

        steps++
        val message = UserMessage(buildMobPrompt(request))
        val response = llm.decide(listOf(message), tools, request)
        log.debug("Действие моба -> {}", response)
        return response
    }

    private fun buildMobPrompt(request: MobDecideRequest): String = """
        Ты — СТРАЖ (элитный моб) в roguelike игре. Твоя задача — атаковать и уничтожить игрока.

        Ты должен отвечать ТОЛЬКО вызовом функции mob_action с одним обязательным полем intent.
        НЕ ДОБАВЛЯЙ другие поля (object, distance, hp, coordinates и т.д.).
        НЕ ПИШИ никакой текст вне вызова функции.

        Допустимые значения intent:
        - "chase"  → если игрок далеко (расстояние > 4.0) и есть путь.
        - "shoot"  → если игрок в пределах 1.5–4.0, есть прямая видимость.
        - "kite"   → если игрок очень близко (< 1.5) – отступить.
        - "idle"   → если игрок вне зоны видимости или ты застрял.
        
        Данные:
        - Твои координаты: (${request.mobX}, ${request.mobY})
        - Координаты игрока: (${request.playerX}, ${request.playerY})
        - Расстояние: ${request.distance}
        - HP игрока: ${request.playerHp}
        
        Выбери действие: преследовать, стрелять, отступать или бездействовать.
    """.trimIndent()
}
