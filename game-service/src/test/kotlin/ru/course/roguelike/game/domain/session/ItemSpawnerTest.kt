package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

class ItemSpawnerTest {
    @Test
    fun `items spawn only on reachable safe floor tiles`() {
        for (seed in 1L..50L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val safeCells = MapConnectivity.reachableSafeFloorCells(level.map, level.playerSpawn)
            val items = ItemSpawner.spawn(level, seed)

            for (item in items) {
                val cell = GridPos(floor(item.x).toInt(), floor(item.y).toInt())
                assertTrue(cell in safeCells, "seed=$seed item at $cell is not in reachable safe floor")
                assertEquals(TileType.FLOOR, level.map.get(cell), "seed=$seed item tile at $cell is not FLOOR")
            }
        }
    }

    @Test
    fun `items avoid occupied cells`() {
        val level = LabyrinthLevelGenerator.generate(7L)
        val firstPass = ItemSpawner.spawn(level, 7L)
        assertTrue(firstPass.isNotEmpty(), "expected at least one item for this seed")
        val occupied = firstPass.map { GridPos(it.x.toInt(), it.y.toInt()) }.toSet()

        val secondPass = ItemSpawner.spawn(level, 7L, occupied = occupied)
        for (item in secondPass) {
            val cell = GridPos(item.x.toInt(), item.y.toInt())
            assertTrue(cell !in occupied, "item spawned on an occupied cell $cell")
        }
    }

    @Test
    fun `spawn is deterministic for a given seed`() {
        val level = LabyrinthLevelGenerator.generate(11L)
        val a = ItemSpawner.spawn(level, 11L)
        val b = ItemSpawner.spawn(level, 11L)
        assertEquals(a, b)
    }

    @Test
    fun `ids start from the given offset and are unique`() {
        val level = LabyrinthLevelGenerator.generate(3L)
        val items = ItemSpawner.spawn(level, 3L, startId = 100)
        val ids = items.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "ids must be unique")
        items.forEach { assertTrue(it.id >= 100, "id ${it.id} below offset") }
    }
}
