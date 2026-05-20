package ru.course.roguelike.game

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.StubGameEngine

class GameServiceTest {
    @Test
    fun `health returns UP`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("game-service"))
    }

    @Test
    fun `create and fetch session`() = testApplication {
        application { module() }
        val create = client.post("/api/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody("""{"seed":42}""")
        }
        assertEquals(HttpStatusCode.OK, create.status)
        assertTrue(create.bodyAsText().contains("sessionId"))
    }

    @Test
    fun `stub engine stores sessions`() {
        val engine = StubGameEngine()
        val session = engine.createSession(7L)
        assertEquals(7L, engine.getSession(session.sessionId)?.seed)
        val action = engine.applyAction(session.sessionId, "move_north")
        assertTrue(action?.accepted == true)
    }
}
