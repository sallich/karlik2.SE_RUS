package ru.course.roguelike.game.domain.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.infrastructure.level.TwoLevelLabyrinthGenerator
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class TwoLevelLabyrinthGeneratorTest {
    private fun elevatorCells(level: GeneratedLevel): Set<GridPos> {
        val map = level.map
        val cells = HashSet<GridPos>()
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (map.get(GridPos(x, y)) == TileType.ELEVATOR) cells.add(GridPos(x, y))
            }
        }
        return cells
    }

    @Test
    fun `produces two levels of equal size`() {
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        assertEquals(2, dungeon.levels.size)
        val a = dungeon.levels[0].map
        val b = dungeon.levels[1].map
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
    }

    @Test
    fun `elevators are placed at identical coordinates on both levels`() {
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        val ground = elevatorCells(dungeon.levels[0])
        val upper = elevatorCells(dungeon.levels[1])
        assertTrue(ground.isNotEmpty(), "expected elevators on the ground level")
        assertEquals(ground, upper, "elevators must be stacked at the same coordinates")
    }

    @Test
    fun `upper tier reuses the same location layout, not a different maze`() {
        // Лифт поднимает на 2-й ярус той же локации, поэтому раскладка обоих
        // ярусов (включая лифты в общих координатах) полностью совпадает.
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        assertEquals(
            dungeon.levels[0].map.toFlatList(),
            dungeon.levels[1].map.toFlatList(),
            "верхний ярус должен повторять раскладку нижнего (та же локация)",
        )
    }

    @Test
    fun `same seed is deterministic`() {
        val a = TwoLevelLabyrinthGenerator.generate(42L)
        val b = TwoLevelLabyrinthGenerator.generate(42L)
        assertEquals(a.levels[0].map.toFlatList(), b.levels[0].map.toFlatList())
        assertEquals(a.levels[1].map.toFlatList(), b.levels[1].map.toFlatList())
    }

    @Test
    fun `each level stays fully connected with elevators in place`() {
        for (seed in 1L..10L) {
            val dungeon = TwoLevelLabyrinthGenerator.generate(seed)
            for (level in dungeon.levels) {
                assertTrue(
                    MapConnectivity.allRoomsReachable(level.map, level.playerSpawn, level.rooms),
                    "seed=$seed level not fully reachable",
                )
            }
        }
    }
}
