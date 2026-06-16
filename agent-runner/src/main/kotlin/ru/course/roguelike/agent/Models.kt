package ru.course.roguelike.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.course.roguelike.agent.llm.YandexFunctionCall
import ru.course.roguelike.agent.llm.YandexFunctionResult
import ru.course.roguelike.agent.llm.YandexToolCall
import ru.course.roguelike.agent.llm.YandexToolCallList
import ru.course.roguelike.agent.llm.YandexToolResult
import ru.course.roguelike.agent.llm.YandexToolResultList

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val llmProvider: String,
    val mcpTransport: String,
)

@Serializable
data class AgentStatusResponse(
    val mode: String,
    val message: String,
    val budgetRemaining: Int,
)

@Serializable
data class AgentRunRequest(
    val seed: Long? = null,
    val maxSteps: Int = 500,
    /** Подключиться к существующей сессии (кооп с игроком). */
    val sessionId: String? = null,
)

@Serializable
data class AgentRunResponse(
    val status: String,
    val message: String,
    val stepsUsed: Int = 0,
    val stepsPlanned: Int = 0,
    val sessionId: String? = null,
    val finalPhase: String? = null,
    val success: Boolean = false,
    val toolCallLog: List<String> = emptyList(),
)

@Serializable
data class MobDecideRequest(
    val mobId: Long,
    val mobX: Float,
    val mobY: Float,
    val playerX: Float,
    val playerY: Float,
    val distance: Float,
    val playerHp: Int,
)

@Serializable
data class MobDecideResponse(
    val intent: String,
    val source: String = "heuristic",
)

sealed class LLMMessage {
    abstract val role: String
    abstract val text: String?
    abstract val toolCallList: ToolCallList?
    abstract val toolResultList: ToolResultList?
}

data class SystemMessage(
    override val text: String,
) : LLMMessage() {
    override val role = "system"
    override val toolCallList = null
    override val toolResultList = null
}

data class UserMessage(
    override val text: String,
) : LLMMessage() {
    override val role = "user"
    override val toolCallList = null
    override val toolResultList = null
}

data class AssistantMessage(
    override val text: String?,
    override val toolCallList: ToolCallList?,
) : LLMMessage() {
    override val role = "assistant"
    override val toolResultList = null
}

data class ToolResultMessage(
    override val toolResultList: ToolResultList,
) : LLMMessage() {
    override val role = "assistant"
    override val text = null
    override val toolCallList = null
}

data class ToolCallList(
    val toolCalls: List<ToolCall>,
)

data class ToolCall(
    val id: String? = null,
    val functionCall: FunctionCall,
)

data class FunctionCall(
    val name: String,
    val arguments: JsonObject,
)

data class ToolResultList(
    val toolResults: List<ToolResult>,
)

data class ToolResult(
    val functionResult: FunctionResult,
)

data class FunctionResult(
    val name: String,
    val content: String,
    val toolCallId: String? = null,
)

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val tools: List<OpenAiTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    val stream: Boolean = false,
)

@Serializable
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>,
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage,
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAiToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
)

@Serializable
data class OpenAiToolCall(
    val id: String,
    val index: Int? = null,
    val type: String,
    val function: OpenAiFunctionCall,
)

@Serializable
data class OpenAiFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class OpenAiTool(
    val type: String,
    val function: OpenAiFunction,
)

@Serializable
data class OpenAiFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class ToolChoice(
    val mode: String, // "required", "auto", или "function"
)

// extensions to convert our internal model to Yandex API model
fun ToolCallList.toYandex(): YandexToolCallList =
    YandexToolCallList(
        toolCalls = toolCalls.map { it.toYandex() },
    )

fun ToolCall.toYandex(): YandexToolCall =
    YandexToolCall(
        functionCall = functionCall.toYandex(),
    )

fun FunctionCall.toYandex(): YandexFunctionCall =
    YandexFunctionCall(
        name = name,
        arguments = arguments,
    )

fun ToolResultList.toYandex(): YandexToolResultList =
    YandexToolResultList(
        toolResults = toolResults.map { it.toYandex() },
    )

fun ToolResult.toYandex(): YandexToolResult =
    YandexToolResult(
        functionResult = functionResult.toYandex(),
    )

fun FunctionResult.toYandex(): YandexFunctionResult =
    YandexFunctionResult(
        name = name,
        content = content,
    )
