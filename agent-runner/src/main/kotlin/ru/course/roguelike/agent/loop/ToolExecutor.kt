package ru.course.roguelike.agent.loop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.FunctionResult
import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.ToolResult
import ru.course.roguelike.agent.ToolResultList
import ru.course.roguelike.agent.ToolResultMessage
import ru.course.roguelike.agent.UserMessage
import ru.course.roguelike.agent.llm.AgentDecisionClient
import ru.course.roguelike.agent.mcp.GameActResponse
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.mcp.McpTool
import ru.course.roguelike.shared.model.SessionPhase

@Suppress("LongParameterList")
class ToolExecutor(
    private val mcp: McpClient,
    private val fallback: AgentDecisionClient,
    private val conversation: MutableList<LLMMessage>,
    private val lastPositions: ArrayDeque<Pair<Int, Int>>,
    private val lastActionKeys: ArrayDeque<String>,
    private val tools: List<McpTool>,
    private val sessionId: String,
    private val actor: String,
    private val toolLog: MutableList<String>,
    private val budget: Int,
) {

    private val log = LoggerFactory.getLogger(ToolExecutor::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun executeAll(
        decisions: List<ToolCallDecision>,
        currentSteps: Int,
        initialSnapshot: GameSnapshot,
    ): ExecutionResult {
        var snapshot = initialSnapshot
        var stepsUsed = 0
        var stepUsed = false

        for (decision in decisions) {
            if (currentSteps + stepsUsed >= budget) {
                log.warn("Budget exhausted before executing all tool calls")
                break
            }

            val result = executeDecision(decision, snapshot)
            snapshot = result.snapshot

            if (!stepUsed) {
                stepsUsed++
                stepUsed = true
            }
            if (result.shouldStop) {
                return ExecutionResult(snapshot, stepsUsed, shouldStop = true)
            }
        }
        return ExecutionResult(snapshot, stepsUsed, shouldStop = false)
    }

    internal suspend fun executeDecision(
        decision: ToolCallDecision,
        snapshotBefore: GameSnapshot,
    ): DecisionExecutionResult {
        val executionResult = callTool(decision)

        val snapshotAfter = executionResult.newSnapshot ?: snapshotBefore

        conversation.add(
            ToolResultMessage(
                toolResultList = ToolResultList(
                    listOf(
                        ToolResult(
                            FunctionResult(
                                decision.tool,
                                executionResult.content,
                                toolCallId = decision.id,
                            ),
                        ),
                    ),
                ),
            ),
        )

        if (executionResult.isError) {
            conversation.add(
                UserMessage("Ошибка: ${executionResult.errorText}. Попробуйте другой инструмент или аргументы."),
            )
        }

        if (!executionResult.isError && (decision.tool == "game_act" || decision.tool == "game_sync")) {
            val description = buildActionResultDescription(snapshotBefore, snapshotAfter, decision)
            conversation.add(UserMessage(description))
        }

        if (detectLoop(snapshotAfter, decision)) {
            log.warn("Loop detected after tool ${decision.tool}, applying fallback")
            val fbResult = applyFallback(snapshotAfter)
            if (fbResult != null) {
                conversation.add(
                    ToolResultMessage(
                        toolResultList = ToolResultList(
                            listOf(
                                ToolResult(
                                    FunctionResult(
                                        fbResult.tool,
                                        fbResult.content,
                                        toolCallId = "fallback_${System.currentTimeMillis()}",
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                conversation.add(
                    UserMessage("Внимание: обнаружено зацикливание. Принудительно выполнен ${fbResult.tool}"),
                )
                return DecisionExecutionResult(fbResult.snapshot, shouldStop = isTerminalPhase(fbResult.snapshot.phase))
            }
        }

        return DecisionExecutionResult(snapshotAfter, shouldStop = isTerminalPhase(snapshotAfter.phase))
    }

    private suspend fun callTool(decision: ToolCallDecision): ToolExecutionResult {
        val args = decision.arguments.toMutableMap().apply {
            if (!containsKey("sessionId")) this["sessionId"] = JsonPrimitive(sessionId)
            if (actor == KeyHuntPlanner.ACTOR_AGENT && !containsKey("actor")) {
                this["actor"] = JsonPrimitive(actor)
            }
        }

        val result = mcp.callTool(decision.tool, args)
        toolLog.add("${decision.tool} -> error=${result.isError}")
        log.info("tool=${decision.tool} err=${result.isError}")

        if (result.isError) {
            return ToolExecutionResult(
                content = json.encodeToString(mapOf("error" to result.text)),
                isError = true,
                errorText = result.text,
                newSnapshot = null,
            )
        }

        return if (decision.tool == "game_act" || decision.tool == "game_sync") {
            val response = json.decodeFromString<GameActResponse>(result.text)
            val compact = buildJsonObject {
                put("accepted", JsonPrimitive(true))
                put(
                    "newPosition",
                    buildJsonObject {
                        put(
                            "x",
                            JsonPrimitive(
                                response.snapshot.agent?.pose?.x ?: response.snapshot.player.pose.x,
                            ),
                        )
                        put(
                            "y",
                            JsonPrimitive(
                                response.snapshot.agent?.pose?.y ?: response.snapshot.player.pose.y,
                            ),
                        )
                    },
                )
                put("keysCollected", JsonPrimitive(response.snapshot.keysCollected))
                put("phase", JsonPrimitive(response.snapshot.phase))
                put("hp", JsonPrimitive(response.snapshot.agent?.hp ?: response.snapshot.player.hp))
            }
            ToolExecutionResult(
                content = json.encodeToString(compact),
                isError = false,
                errorText = null,
                newSnapshot = response.snapshot,
            )
        } else {
            ToolExecutionResult(
                content = result.text,
                isError = false,
                errorText = null,
                newSnapshot = null,
            )
        }
    }

    internal fun detectLoop(
        snapshot: GameSnapshot,
        decision: ToolCallDecision,
    ): Boolean {
        val px = (snapshot.agent?.pose?.x ?: snapshot.player.pose.x).toInt()
        val py = (snapshot.agent?.pose?.y ?: snapshot.player.pose.y).toInt()
        val posKey = px to py
        lastPositions.addLast(posKey)
        if (lastPositions.size > 3) lastPositions.removeFirst()
        val stuckInPlace = lastPositions.size == 3 && lastPositions.all { it == lastPositions.first() }

        val actionKey =
            "${decision.tool}:${decision.arguments.filterNot { it.key == "sessionId" || it.key == "actor" }}"
        lastActionKeys.addLast(actionKey)
        if (lastActionKeys.size > 10) lastActionKeys.removeFirst()
        val repeatedAction = lastActionKeys.size == 10 && lastActionKeys.toSet().size == 1

        return stuckInPlace || repeatedAction
    }

    private fun buildActionResultDescription(
        before: GameSnapshot,
        after: GameSnapshot,
        decision: ToolCallDecision,
    ): String {
        val oldPos = before.agent?.pose ?: before.player.pose
        val newPos = after.agent?.pose ?: after.player.pose
        val actionName = decision.arguments["action"]?.jsonPrimitive?.content ?: decision.tool

        return buildString {
            append("Ты выполнил: $actionName. ")
            if (oldPos.x != newPos.x || oldPos.y != newPos.y) {
                append("Переместился из (${oldPos.x}, ${oldPos.y}) в (${newPos.x}, ${newPos.y}). ")
            } else {
                append("Позиция не изменилась. ")
            }
            if (after.keysCollected > before.keysCollected) {
                append("Подобрал ключ! Теперь ключей: ${after.keysCollected}/${after.keysRequired}. ")
            }
            when (after.phase) {
                SessionPhase.LEVEL_COMPLETE.name -> append("УРОВЕНЬ ПРОЙДЕН! ")
                SessionPhase.GAME_OVER.name -> append("ИГРА ОКОНЧЕНА. ")
            }
            val newHp = after.agent?.hp ?: after.player.hp
            val oldHp = before.agent?.hp ?: before.player.hp
            if (newHp < oldHp) {
                append("Потеряно ${oldHp - newHp} HP. Осталось $newHp. ")
            }
        }
    }

    private suspend fun applyFallback(snapshot: GameSnapshot): FallbackOutcome? {
        val fallbackDecision = fallback.chooseTool(snapshot, sessionId, conversation, tools, actor)
        if (fallbackDecision.isEmpty()) return null

        val fb = fallbackDecision.first()
        val fbArgs = fb.arguments.toMutableMap().apply {
            if (!containsKey("sessionId")) this["sessionId"] = JsonPrimitive(sessionId)
            if (actor == KeyHuntPlanner.ACTOR_AGENT && !containsKey("actor")) {
                this["actor"] = JsonPrimitive(actor)
            }
        }
        val fbResult = mcp.callTool(fb.tool, fbArgs)
        toolLog.add("fallback ${fb.tool} -> error=${fbResult.isError}")

        val newSnapshot = if (fb.tool == "game_act" || fb.tool == "game_sync") {
            val response = json.decodeFromString<GameActResponse>(fbResult.text)
            response.snapshot
        } else {
            snapshot
        }

        val content = if (fbResult.isError) {
            json.encodeToString(mapOf("error" to fbResult.text))
        } else {
            fbResult.text
        }

        return FallbackOutcome(newSnapshot, fb.tool, content)
    }

    private fun isTerminalPhase(phase: String): Boolean =
        phase == SessionPhase.LEVEL_COMPLETE.name || phase == SessionPhase.GAME_OVER.name

    private data class FallbackOutcome(
        val snapshot: GameSnapshot,
        val tool: String,
        val content: String,
    )

    data class ToolExecutionResult(
        val content: String,
        val isError: Boolean,
        val errorText: String?,
        val newSnapshot: GameSnapshot?,
    )

    data class DecisionExecutionResult(
        val snapshot: GameSnapshot,
        val shouldStop: Boolean,
    )

    data class ExecutionResult(
        val snapshot: GameSnapshot,
        val stepsUsed: Int,
        val shouldStop: Boolean,
    )
}
