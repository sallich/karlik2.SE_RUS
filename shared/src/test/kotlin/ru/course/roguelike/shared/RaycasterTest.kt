package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.render.Raycaster

class RaycasterTest {
    @Test
    fun `casts columns in enclosed room`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[0] = TileType.WALL
        tiles[2] = TileType.WALL
        val map = TileMap(3, 3, tiles)
        val cols = Raycaster.castColumns(map, PlayerPose(1.5f, 1.5f, 0f), 32, 24, horizonY = 12f)
        assertTrue(cols.any { it.wallEnd > it.wallStart + 1f })
    }
}
