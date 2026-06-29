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
    fun `produces a single level with elevators`() {
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        assertEquals(1, dungeon.levels.size)
        assertTrue(elevatorCells(dungeon.levels.single()).isNotEmpty())
    }

    @Test
    fun `columns remain on the same level`() {
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        val tiles = dungeon.levels.single().map.toFlatList()
        assertTrue(tiles.any { it == TileType.COLUMN })
    }

    @Test
    fun `same seed is deterministic`() {
        val a = TwoLevelLabyrinthGenerator.generate(42L)
        val b = TwoLevelLabyrinthGenerator.generate(42L)
        assertEquals(a.levels.single().map.toFlatList(), b.levels.single().map.toFlatList())
    }

    @Test
    fun `every elevator is generated next to a column`() {
        for (seed in 1L..20L) {
            val dungeon = TwoLevelLabyrinthGenerator.generate(seed)
            val map = dungeon.levels.single().map
            val elevators = elevatorCells(dungeon.levels.single())
            assertTrue(elevators.isNotEmpty(), "seed=$seed produced no elevators")
            for (cell in elevators) {
                val beside = neighbors(cell).any { map.get(it) == TileType.COLUMN }
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
    fun `level stays fully connected with elevators in place`() {
        for (seed in 1L..10L) {
            val dungeon = TwoLevelLabyrinthGenerator.generate(seed)
            val level = dungeon.levels.single()
            assertTrue(
                MapConnectivity.allRoomsReachable(level.map, level.playerSpawn, level.rooms),
                "seed=$seed level not fully reachable",
            )
        }
    }
}
