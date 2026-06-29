package ru.course.roguelike.mcp

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpServerTest {
    @Test
    fun `lists MCP tools`() = testApplication {
        application { module() }
        val response = client.get("/mcp/tools")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("game_new_session"))
    }
}
