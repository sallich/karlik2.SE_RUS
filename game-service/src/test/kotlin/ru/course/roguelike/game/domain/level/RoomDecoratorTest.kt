package ru.course.roguelike.game.domain.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class RoomDecoratorTest {
    private fun countTiles(seed: Long): Map<TileType, Int> {
        val level = LabyrinthLevelGenerator.generate(seed)
        val counts = HashMap<TileType, Int>()
        for (tile in level.map.toFlatList()) {
            counts[tile] = (counts[tile] ?: 0) + 1
        }
        return counts
    }

    @Test
    fun `decoration places both columns and lava`() {
        for (seed in 1L..10L) {
            val counts = countTiles(seed)
            assertTrue((counts[TileType.COLUMN] ?: 0) > 0, "seed=$seed produced no columns")
            assertTrue((counts[TileType.LAVA] ?: 0) > 0, "seed=$seed produced no lava")
        }
    }

    @Test
    fun `spawn stays clear floor after decoration`() {
        for (seed in 1L..10L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            assertEquals(TileType.FLOOR, level.map.get(level.playerSpawn), "seed=$seed decorated over spawn")
        }
    }

    @Test
    fun `boss room is an arena - columns allowed but no lava`() {
        for (seed in 1L..10L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val boss = level.rooms.single { it.isBoss }
            for (y in boss.y until boss.y + boss.height) {
                for (x in boss.x until boss.x + boss.width) {
                    assertTrue(
                        level.map.get(GridPos(x, y)) != TileType.LAVA,
                        "seed=$seed boss room has lava at ($x,$y)",
                    )
                }
            }
        }
    }

    @Test
    fun `every room keeps its center walkable`() {
        for (seed in 1L..10L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            for (room in level.rooms) {
                assertEquals(
                    TileType.FLOOR,
                    level.map.get(room.center),
                    "seed=$seed room center ${room.center} was decorated",
                )
            }
        }
    }
}
