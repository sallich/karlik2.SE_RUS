package ru.course.roguelike.mcp.protocol

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.mcp.client.GameServiceClient

class McpToolRegistryTest {
    private val registry = McpToolRegistry(
        GameServiceClient("http://localhost:65535"),
    )

    @Test
    fun `registry exposes six tools`() {
        assertEquals(6, registry.descriptors().size)
        assertTrue(McpToolDefinitions.NAMES.contains("game_sync"))
        assertTrue(McpToolDefinitions.NAMES.contains("game_list_actions"))
    }

    @Test
    fun `invalid game_act arguments return error`() = runBlocking {
        val missingSession = registry.invoke("game_act", emptyMap())
        assertTrue(missingSession.isError)
        assertTrue(missingSession.content.first().text.contains("sessionId"))

        val badAction = registry.invoke(
            "game_act",
            buildJsonObject {
                put("sessionId", "s1")
                put("action", "fly")
            }.mapValues { it.value },
        )
        assertTrue(badAction.isError)
        assertTrue(badAction.content.first().text.contains("Invalid action"))
    }

    @Test
    fun `game_list_actions returns action list`() = runBlocking {
        val result = registry.invoke("game_list_actions", emptyMap())
        org.junit.jupiter.api.Assertions.assertFalse(result.isError)
        assertTrue(result.content.first().text.contains("move_forward"))
    }
}
