package ru.course.roguelike.game.domain.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `upper tier replaces columns with walkable floor`() {
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        val ground = dungeon.levels[0].map.toFlatList()
        val upper = dungeon.levels[1].map.toFlatList()
        assertTrue(ground.any { it == TileType.COLUMN })
        assertFalse(upper.any { it == TileType.COLUMN }, "upper walkable deck over column grid")
        for (i in ground.indices) {
            if (ground[i] == TileType.COLUMN) {
                assertEquals(TileType.FLOOR, upper[i])
            }
        }
    }

    @Test
    fun `same seed is deterministic`() {
        val a = TwoLevelLabyrinthGenerator.generate(42L)
        val b = TwoLevelLabyrinthGenerator.generate(42L)
        assertEquals(a.levels[0].map.toFlatList(), b.levels[0].map.toFlatList())
        assertEquals(a.levels[1].map.toFlatList(), b.levels[1].map.toFlatList())
    }

    @Test
    fun `every elevator is generated next to a column`() {
        // issue: подниматься имеет смысл только у колонн, поэтому лифты ставятся
        // вплотную к ним (8 соседей).
        for (seed in 1L..20L) {
            val dungeon = TwoLevelLabyrinthGenerator.generate(seed)
            val ground = dungeon.levels[0].map
            val elevators = elevatorCells(dungeon.levels[0])
            assertTrue(elevators.isNotEmpty(), "seed=$seed produced no elevators")
            for (cell in elevators) {
                val beside = neighbors(cell).any { ground.get(it) == TileType.COLUMN }
                assertTrue(beside, "seed=$seed elevator at $cell is not next to a column")
            }
        }
    }

    private fun neighbors(cell: GridPos): List<GridPos> = buildList {
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx != 0 || dy != 0) add(GridPos(cell.x + dx, cell.y + dy))
            }
        }
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
