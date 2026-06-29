package ru.course.roguelike.agent.mcp

import ru.course.roguelike.agent.config.AgentConfig

object McpClientFactory {
    fun create(config: AgentConfig): McpClient =
        when (config.mcpTransport.lowercase()) {
            "http" -> HttpMcpClient.fromConfig(config)
            else -> StdioMcpClient.fromConfig(config)
        }
}
