package ru.course.roguelike.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val tools: List<OpenAiTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    val stream: Boolean = false,
    val options: OllamaChatOptions? = null,
    @SerialName("keep_alive")
    val keepAlive: String? = null,
)

@Serializable
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val error: OpenAiApiError? = null,
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
