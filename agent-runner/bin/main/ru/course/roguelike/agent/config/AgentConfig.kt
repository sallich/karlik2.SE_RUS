package ru.course.roguelike.agent.config

data class AgentConfig(
    val llmProvider: String,
    val llmApiKey: String?,
    val yandexFolderId: String?,
    val mcpCommand: List<String>,
    val mcpTransport: String,
    val mcpServerUrl: String,
    val maxToolCalls: Int,
    val retryAttempts: Int,
) {
    companion object {
        fun fromEnvironment(): AgentConfig {
            val mcpCommand = env("MCP_SERVER_COMMAND")
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                ?: listOf("./gradlew", ":mcp-server:run", "--args=stdio")

            return AgentConfig(
                llmProvider = env("LLM_PROVIDER") ?: "heuristic",
                llmApiKey = env("LLM_API_KEY"),
                yandexFolderId = env("YANDEX_FOLDER_ID"),
                mcpCommand = mcpCommand,
                mcpTransport = env("MCP_TRANSPORT") ?: "stdio",
                mcpServerUrl = env("MCP_SERVER_URL") ?: "http://localhost:8081",
                maxToolCalls = env("AGENT_MAX_TOOL_CALLS")?.toIntOrNull() ?: 500,
                retryAttempts = env("AGENT_RETRY_ATTEMPTS")?.toIntOrNull() ?: 3,
            )
        }

        private fun env(name: String): String? =
            System.getenv(name) ?: System.getProperty(name)
    }
}
