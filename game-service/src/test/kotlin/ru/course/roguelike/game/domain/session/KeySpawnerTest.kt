package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

class KeySpawnerTest {
    @Test
    fun `keys spawn only on reachable safe floor tiles`() {
        for (seed in 1L..50L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val safeCells = MapConnectivity.reachableSafeFloorCells(level.map, level.playerSpawn)
            val keys = KeySpawner.spawn(level, seed)

            assertTrue(keys.isNotEmpty(), "seed=$seed produced no keys")
            for (key in keys) {
                val cell = GridPos(floor(key.x).toInt(), floor(key.y).toInt())
                assertTrue(cell in safeCells, "seed=$seed key at $cell is not in reachable safe floor")
                assertEquals(TileType.FLOOR, level.map.get(cell), "seed=$seed key tile at $cell is not FLOOR")
            }
        }
    }
}
