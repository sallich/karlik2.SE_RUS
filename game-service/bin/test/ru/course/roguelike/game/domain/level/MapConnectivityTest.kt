package ru.course.roguelike.game.domain.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class MapConnectivityTest {
    // Карта 5x3: два изолированных пола в (1,1) и (3,1), между ними стена.
    private fun disconnectedMap(): TileMap {
        val tiles = Array(5 * 3) { TileType.WALL }
        tiles[1 * 5 + 1] = TileType.FLOOR
        tiles[1 * 5 + 3] = TileType.FLOOR
        return TileMap(5, 3, tiles)
    }

    @Test
    fun `flood fill stops at walls`() {
        val map = disconnectedMap()
        val reachable = MapConnectivity.reachableFrom(map, GridPos(1, 1))
        assertEquals(setOf(GridPos(1, 1)), reachable)
    }

    @Test
    fun `isolated room is reported as unreachable`() {
        val map = disconnectedMap()
        val rooms = listOf(Room(1, 1, 1, 1), Room(3, 1, 1, 1, isBoss = true))
        val start = GridPos(1, 1)

        assertFalse(MapConnectivity.allRoomsReachable(map, start, rooms))
        assertEquals(listOf(Room(3, 1, 1, 1, isBoss = true)), MapConnectivity.unreachableRooms(map, start, rooms))
    }

    @Test
    fun `carving a corridor makes the far room reachable`() {
        val tiles = Array(5 * 3) { TileType.WALL }
        tiles[1 * 5 + 1] = TileType.FLOOR
        tiles[1 * 5 + 2] = TileType.FLOOR // мостик-коридор
        tiles[1 * 5 + 3] = TileType.FLOOR
        val map = TileMap(5, 3, tiles)
        val rooms = listOf(Room(1, 1, 1, 1), Room(3, 1, 1, 1, isBoss = true))

        assertTrue(MapConnectivity.allRoomsReachable(map, GridPos(1, 1), rooms))
    }

    @Test
    fun `lava is treated as walkable for connectivity`() {
        // Пол -> лава -> пол: лава проходима, значит дальняя комната достижима.
        val tiles = Array(5 * 3) { TileType.WALL }
        tiles[1 * 5 + 1] = TileType.FLOOR
        tiles[1 * 5 + 2] = TileType.LAVA
        tiles[1 * 5 + 3] = TileType.FLOOR
        val map = TileMap(5, 3, tiles)

        assertEquals(3, MapConnectivity.reachableFrom(map, GridPos(1, 1)).size)
    }

    @Test
    fun `column blocks connectivity`() {
        val tiles = Array(5 * 3) { TileType.WALL }
        tiles[1 * 5 + 1] = TileType.FLOOR
        tiles[1 * 5 + 2] = TileType.COLUMN
        tiles[1 * 5 + 3] = TileType.FLOOR
        val map = TileMap(5, 3, tiles)

        assertEquals(setOf(GridPos(1, 1)), MapConnectivity.reachableFrom(map, GridPos(1, 1)))
    }

    @Test
    fun `generated labyrinths are fully connected and every room including boss is reachable`() {
        for (seed in 1L..25L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val reachable = MapConnectivity.reachableFrom(level.map, level.playerSpawn)

            assertTrue(
                MapConnectivity.allRoomsReachable(level.map, level.playerSpawn, level.rooms),
                "seed=$seed has an unreachable room",
            )
            assertTrue(
                MapConnectivity.isFullyConnected(level.map, level.playerSpawn),
                "seed=$seed has an isolated walkable pocket",
            )
            val boss = level.rooms.single { it.isBoss }
            assertTrue(boss.center in reachable, "seed=$seed boss room not reachable from spawn")
        }
    }
}
