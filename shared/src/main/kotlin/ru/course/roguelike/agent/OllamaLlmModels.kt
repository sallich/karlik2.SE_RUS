package ru.course.roguelike.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatOptions(
    @SerialName("num_ctx") val numCtx: Int? = null,
    @SerialName("num_predict") val numPredict: Int? = null,
    val temperature: Float? = null,
    val seed: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
)

@Serializable
data class OpenAiApiError(
    val message: String? = null,
    val type: String? = null,
)
