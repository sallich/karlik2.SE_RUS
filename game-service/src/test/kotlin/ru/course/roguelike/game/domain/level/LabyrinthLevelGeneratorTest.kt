package ru.course.roguelike.game.domain.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class LabyrinthLevelGeneratorTest {
    @Test
    fun `same seed produces identical level`() {
        val a = LabyrinthLevelGenerator.generate(42L)
        val b = LabyrinthLevelGenerator.generate(42L)

        assertEquals(a.playerSpawn, b.playerSpawn)
        assertEquals(a.rooms, b.rooms)
        assertEquals(a.map.toFlatList(), b.map.toFlatList())
    }

    @Test
    fun `different seeds produce different layouts`() {
        val a = LabyrinthLevelGenerator.generate(1L)
        val b = LabyrinthLevelGenerator.generate(2L)
        assertTrue(a.map.toFlatList() != b.map.toFlatList())
    }

    @Test
    fun `generates multiple rooms`() {
        val level = LabyrinthLevelGenerator.generate(7L)
        assertTrue(level.rooms.size > 1, "expected several rooms, got ${level.rooms.size}")
    }

    @Test
    fun `spawn is on a floor tile inside the starting room`() {
        val level = LabyrinthLevelGenerator.generate(7L)
        assertEquals(TileType.FLOOR, level.map.get(level.playerSpawn))
        assertTrue(level.rooms.first().contains(level.playerSpawn))
    }

    @Test
    fun `player never spawns on lava`() {
        // issue #13: декор может ставить лаву в стартовой комнате, но не на спавне.
        for (seed in 1L..100L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            assertEquals(
                TileType.FLOOR,
                level.map.get(level.playerSpawn),
                "seed=$seed spawned the player on a non-floor tile",
            )
        }
    }

    @Test
    fun `exactly one boss room and it is larger than every normal room`() {
        val level = LabyrinthLevelGenerator.generate(7L)
        val bossRooms = level.rooms.filter { it.isBoss }
        assertEquals(1, bossRooms.size)

        val boss = bossRooms.single()
        val largestNormal = level.rooms.filterNot { it.isBoss }.maxOf { it.area }
        assertTrue(boss.area > largestNormal, "boss area ${boss.area} should exceed normal max $largestNormal")
    }

    @Test
    fun `corridors carve floor outside of rooms`() {
        val level = LabyrinthLevelGenerator.generate(7L)
        val map = level.map
        var corridorFloors = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val pos = GridPos(x, y)
                if (map.get(pos) == TileType.FLOOR && level.rooms.none { it.contains(pos) }) {
                    corridorFloors++
                }
            }
        }
        assertTrue(corridorFloors > 0, "expected corridor floor tiles connecting rooms")
    }
}
