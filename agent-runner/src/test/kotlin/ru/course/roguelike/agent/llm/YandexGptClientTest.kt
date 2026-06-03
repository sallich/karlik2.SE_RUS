package ru.course.roguelike.agent.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType


class YandexGptClientTest {
    @Test
    fun `loadToolsFromContract parses tools correctly`() {
        val config = AgentConfig(
            llmProvider = "yandex",
            llmApiKey = "aboba",
            yandexFolderId = "aboba",
            mcpCommand = listOf("./gradlew", ":mcp-server:run", "--args=stdio"),
            mcpTransport = "stdio",
            mcpServerUrl = "http://localhost:8081",
            maxToolCalls = 5,
            retryAttempts = 3,
        )
        val fallback = object : AgentDecisionClient {
            override suspend fun chooseTool(snapshot: GameSnapshot, sessionId: String, actor: String): ToolCallDecision =
                ToolCallDecision(
                    tool = "game_act",
                    arguments = mapOf(
                        "action" to JsonPrimitive("wait"),
                        "sessionId" to JsonPrimitive(sessionId)
                    ))
        }
        val client = YandexGptClient(config, fallback)
        val tools = client.getToolsName()

        assertTrue(tools.isNotEmpty())

        val expected = listOf("game_new_session", "game_observe", "game_act", "game_sync", "game_session_summary", "game_list_actions")

        assertEquals(expected, tools)
    }

}

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "YANDEX_API_KEY", matches = ".+")
class YandexGptIntegrationTest {

    @Test
    fun `real call with tool use`() = runBlocking {
        val apiKey = System.getenv("YANDEX_API_KEY")
        val folderId = System.getenv("YANDEX_FOLDER_ID")
        val config = AgentConfig(
            llmProvider = "yandex",
            llmApiKey = apiKey,
            yandexFolderId = folderId,
            mcpCommand = listOf("./gradlew", ":mcp-server:run", "--args=stdio"),
            mcpTransport = "stdio",
            mcpServerUrl = "http://localhost:8081",
            maxToolCalls = 5,
            retryAttempts = 3,
        )
        val fallback = object : AgentDecisionClient {
            override suspend fun chooseTool(snapshot: GameSnapshot, sessionId: String, actor: String): ToolCallDecision =
                ToolCallDecision(
                    tool = "game_act",
                    arguments = mapOf(
                        "action" to JsonPrimitive("wait"),
                        "sessionId" to JsonPrimitive(sessionId)
                    ))
        }
        val client = YandexGptClient(config, fallback)
        val snapshot = dummySnapshot()
        val decision = client.chooseTool(snapshot, "integration-test", "tester")
        println("Decision: $decision")
        assertNotNull(decision)
    }

    private fun dummySnapshot(): GameSnapshot {
        return GameSnapshot(
            sessionId = "dummy-session",
            seed = 42,
            phase = "EXPLORATION",
            width = 10,
            height = 10,
            tiles = List(100) { TileType.FLOOR },
            player = PlayerSnapshot(
                pose = PlayerPose(x = 5f, y = 5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
                level = 1,
                experience = 0,
                experienceToNextLevel = 100,
                attackDamage = 25
            ),
            agent = null,
            tick = 0,
            serverTimeMs = System.currentTimeMillis(),
            currentLevel = 0,
            mobs = emptyList(),
            projectiles = emptyList(),
            keysCollected = 0,
            keysRequired = 3,
            keyPickups = emptyList(),
            bossRoom = null,
            exitGate = null
        )
    }
}