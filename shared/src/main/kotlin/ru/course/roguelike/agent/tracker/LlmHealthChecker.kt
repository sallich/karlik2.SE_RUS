package ru.course.roguelike.agent.tracker

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.course.roguelike.agent.config.AgentConfig

object LlmHealthChecker {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 4_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun check(config: AgentConfig): LlmHealthResponse {
        val provider = config.llmProvider.lowercase()
        if (provider != "ollama") {
            return LlmHealthResponse(
                provider = provider,
                reachable = true,
                hint = "LLM provider is '$provider' (not ollama). Set LLM_PROVIDER=ollama to use local models.",
            )
        }
        val base = config.ollamaBaseUrl.trimEnd('/')
        return runCatching {
            val tags = client.get("$base/api/tags").body<OllamaTagsResponse>()
            val names = tags.models.map { it.name }
            val primary = config.ollamaModel
            val fallback = config.ollamaFallbackModel
            val primaryOk = names.any { it == primary || it.startsWith("$primary:") }
            val fallbackOk = names.any { it == fallback || it.startsWith("$fallback:") }
            LlmHealthResponse(
                provider = provider,
                reachable = true,
                ollamaBaseUrl = base,
                models = names,
                configuredModel = primary,
                configuredFallback = fallback,
                modelAvailable = primaryOk,
                fallbackAvailable = fallbackOk,
                hint = when {
                    names.isEmpty() -> "Ollama reachable but no models. Run: ollama pull $primary"
                    !primaryOk -> "Model '$primary' not found. Available: ${names.take(5).joinToString()}"
                    base.contains("host.docker.internal") ->
                        "Connected to Ollama on Windows host."
                    else -> "Ollama OK."
                },
            )
        }.getOrElse { ex ->
            LlmHealthResponse(
                provider = provider,
                reachable = false,
                ollamaBaseUrl = base,
                configuredModel = config.ollamaModel,
                configuredFallback = config.ollamaFallbackModel,
                error = ex.message,
                hint = buildString {
                    append("Cannot reach Ollama at $base. ")
                    append("WSL+Docker: set OLLAMA_BASE_URL=http://host.docker.internal:11434 ")
                    append("and run Ollama on Windows. Or: docker compose --profile llm up")
                },
            )
        }
    }

    @Serializable
    private data class OllamaTagsResponse(val models: List<OllamaModelTag> = emptyList())

    @Serializable
    private data class OllamaModelTag(val name: String)
}
