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
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.dto.InputSyncRequest

class GameServiceTest {
    @Test
    fun `health returns UP`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("game-service"))
    }

    @Test
    fun `sync moves player`() = testApplication {
        application { module() }
        val create = client.post("/api/v1/sessions") {
            contentType(ContentType.Application.Json)
            setBody("""{"seed":42}""")
        }
        assertEquals(HttpStatusCode.OK, create.status)
        val body = create.bodyAsText()
        val sessionId = """"sessionId":"([^"]+)"""".toRegex().find(body)?.groupValues?.get(1)
            ?: error("no sessionId")

        val sync = client.post("/api/v1/sessions/$sessionId/sync") {
            contentType(ContentType.Application.Json)
            setBody("""{"forward":true,"deltaMs":200}""")
        }
        assertEquals(HttpStatusCode.OK, sync.status)
        assertTrue(sync.bodyAsText().contains("\"accepted\":true"))
        assertTrue(sync.bodyAsText().contains("\"pose\""))
    }

    @Test
    fun `fps movement does not enter wall`() {
        val map = TileMap(
            width = 3,
            height = 3,
            tiles = arrayOf(
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
                TileType.FLOOR, TileType.FLOOR, TileType.WALL,
                TileType.FLOOR, TileType.FLOOR, TileType.FLOOR,
            ),
        )
        val start = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val moved = FpsMovementSystem.applyInput(
            map,
            start,
            InputSyncRequest(forward = true, deltaMs = 500),
        )
        assertTrue(moved.x < 2f)
    }
}
