package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.render.CameraProjection
import ru.course.roguelike.shared.render.Raycaster

class CameraProjectionTest {
    @Test
    fun `wall and sprite feet align with floor plane at same distance`() {
        val pitchHorizon = 135f
        val dist = 3f
        val screenHeight = 270
        val lineHeight = screenHeight / dist
        val (_, wallBottom) = CameraProjection.projectWallSpan(
            pitchHorizon,
            lineHeight,
            wallHeight = 1f,
            screenHeight,
            dist,
            viewerHeightAboveFloor = 0f,
        )
        val (_, spriteBottom) = CameraProjection.projectSpriteSpan(
            pitchHorizon,
            spriteHeight = 40,
            screenHeight,
            dist,
            viewerHeight = 0f,
        )
        assertEquals(wallBottom.toInt(), spriteBottom, "mob feet sit on the same floor line as walls")
    }

    @Test
    fun `world floor screen y matches floor distance inversion`() {
        val pitchHorizon = 100f
        val screenHeight = 200
        val row = 150f
        val dist = Raycaster.floorDistance(screenHeight, pitchHorizon, row.toInt(), localHeight = 0f)
        val y = CameraProjection.worldFloorScreenY(pitchHorizon, screenHeight, dist, viewerHeightAboveFloor = 0f)
        assertEquals(row, y, 0.5f)
    }

    @Test
    fun `grounded wall bottom sits below pitch horizon at finite distance`() {
        val pitchHorizon = 135f
        val dist = 2f
        val screenHeight = 270
        val expected = pitchHorizon + screenHeight * 0.5f / dist
        val (_, bottom) = CameraProjection.projectWallSpan(
            pitchHorizon,
            lineHeight = screenHeight / dist,
            wallHeight = 1f,
            screenHeight,
            dist,
            viewerHeightAboveFloor = 0f,
        )
        assertEquals(expected, bottom, 0.01f)
    }
}
