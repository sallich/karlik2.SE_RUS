package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator

class RoomEngagementIntegrationTest {
    @Test
    fun `labyrinth rooms have doorways and door markers at session start`() {
        val engine = GameEngine()
        val snap = engine.createSession(seed = 42L, twoLevel = true)

        val level = LabyrinthLevelGenerator.generate(42L)
        val doorwayCounts = level.rooms.map { RoomDoorways.of(level.map, it).size }
        assertTrue(doorwayCounts.any { it > 0 }, "expected at least one room with doorways: $doorwayCounts")
        assertTrue(
            snap.doorMarkers.isNotEmpty(),
            "expected door markers for rooms with prizes or mobs, got ${snap.doorMarkers.size}",
        )
    }
}
