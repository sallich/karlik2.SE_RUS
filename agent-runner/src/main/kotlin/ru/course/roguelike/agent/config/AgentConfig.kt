package ru.course.roguelike.agent.config

data class AgentConfig(
    val llmProvider: String,
    val llmApiKey: String?,
    val mcpCommand: List<String>,
    val mcpTransport: String,
    val maxToolCalls: Int,
    val retryAttempts: Int,
) {
    companion object {
        fun fromEnvironment(): AgentConfig {
            val mcpCommand = System.getenv("MCP_SERVER_COMMAND")
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                ?: listOf("echo", "mcp-server-stub")

            return AgentConfig(
                llmProvider = System.getenv("LLM_PROVIDER") ?: "stub",
                llmApiKey = System.getenv("LLM_API_KEY"),
                mcpCommand = mcpCommand,
                mcpTransport = System.getenv("MCP_TRANSPORT") ?: "stdio",
                maxToolCalls = System.getenv("AGENT_MAX_TOOL_CALLS")?.toIntOrNull() ?: 100,
                retryAttempts = System.getenv("AGENT_RETRY_ATTEMPTS")?.toIntOrNull() ?: 3,
            )
        }
    }
}
