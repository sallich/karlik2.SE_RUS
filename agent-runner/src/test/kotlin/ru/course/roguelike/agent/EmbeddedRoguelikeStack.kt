package ru.course.roguelike.agent

import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.mcp.protocol.McpToolRegistry

object EmbeddedRoguelikeStack {
    data class RunningServers(
        val engine: GameEngine,
        val registry: McpToolRegistry,
    )

    fun start(): RunningServers {
        val engine = GameEngine()
        val port = LocalGameSessionClient(engine)
        return RunningServers(engine, McpToolRegistry(port))
    }

    fun stop(@Suppress("UNUSED_PARAMETER") servers: RunningServers) = Unit
}
