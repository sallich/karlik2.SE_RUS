package ru.course.roguelike.agent.config

data class AgentConfig(
    val llmProvider: String,
    val llmApiKey: String?,
    val yandexFolderId: String?,
    val ollamaBaseUrl: String = "http://localhost:11434",
    val ollamaModel: String = "qwen2.5:3b",
    val ollamaFallbackModel: String = "qwen2.5:0.5b",
    val llmRequestTimeoutMs: Long = 120_000L,
    val llmFallbackTimeoutMs: Long = 45_000L,
    val useHeuristicFallback: Boolean = false,
    val mcpCommand: List<String>,
    val mcpTransport: String,
    val mcpServerUrl: String,
    val maxToolCalls: Int,
    val retryAttempts: Int,
    val llmMaxHistoryMessages: Int = 16,
    val ollamaNumCtx: Int = 8192,
    val ollamaNumPredict: Int = 256,
    val maxMobToolCalls: Int = 30,
    val allowedTools: Set<String> = setOf("game_act"),
) {
    /** Совместимость с agent-runner (ветка llm-boss): имя модели Ollama. */
    val ollamaModelUrl: String get() = ollamaModel

    companion object {
        fun fromEnvironment(): AgentConfig {
            val mcpCommand = env("MCP_SERVER_COMMAND")
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                ?: listOf("./gradlew", ":mcp-server:run", "--args=stdio")

            val primaryModel = env("OLLAMA_MODEL") ?: env("OLLAMA_MODEL_URL") ?: "qwen2.5:3b"
            val fallbackModel = env("OLLAMA_FALLBACK_MODEL") ?: "qwen2.5:0.5b"

            return AgentConfig(
                llmProvider = env("LLM_PROVIDER") ?: "heuristic",
                llmApiKey = env("LLM_API_KEY"),
                yandexFolderId = env("YANDEX_FOLDER_ID"),
                ollamaBaseUrl = env("OLLAMA_BASE_URL") ?: "http://localhost:11434",
                ollamaModel = primaryModel,
                ollamaFallbackModel = fallbackModel,
                llmRequestTimeoutMs = env("LLM_REQUEST_TIMEOUT_MS")?.toLongOrNull() ?: 120_000L,
                llmFallbackTimeoutMs = env("LLM_FALLBACK_TIMEOUT_MS")?.toLongOrNull() ?: 45_000L,
                useHeuristicFallback = env("USE_HEURISTIC_FALLBACK")?.toBooleanStrictOrNull() ?: false,
                mcpCommand = mcpCommand,
                mcpTransport = env("MCP_TRANSPORT") ?: "stdio",
                mcpServerUrl = env("MCP_SERVER_URL") ?: "http://localhost:8081",
                maxToolCalls = env("AGENT_MAX_TOOL_CALLS")?.toIntOrNull() ?: 500,
                retryAttempts = env("AGENT_RETRY_ATTEMPTS")?.toIntOrNull() ?: 1,
                llmMaxHistoryMessages = env("LLM_MAX_HISTORY_MESSAGES")?.toIntOrNull() ?: 16,
                ollamaNumCtx = env("OLLAMA_NUM_CTX")?.toIntOrNull() ?: 8192,
                ollamaNumPredict = env("OLLAMA_NUM_PREDICT")?.toIntOrNull() ?: 256,
                maxMobToolCalls = env("MOB_MAX_TOOL_CALLS")?.toIntOrNull() ?: 30,
            )
        }

        private fun env(name: String): String? =
            System.getenv(name) ?: System.getProperty(name)
    }
}
