package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun `column blocks the ray like a wall`() {
        // Игрок смотрит на восток (yaw=0); впереди по центру колонна.
        val tiles = Array(15) { TileType.FLOOR } // 5x3
        tiles[1 * 5 + 3] = TileType.COLUMN
        val map = TileMap(5, 3, tiles)
        val cols = Raycaster.castColumns(map, PlayerPose(1.5f, 1.5f, yaw = 0f), 32, 24, horizonY = 12f)
        val center = cols[cols.size / 2]
        assertTrue(center.wallEnd > center.wallStart + 1f, "column should project a solid vertical span")
    }

    @Test
    fun `lava does not block the ray`() {
        // Та же геометрия, но впереди лава вместо колонны — луч проходит до дальней стены.
        val lavaTiles = Array(15) { TileType.FLOOR }
        lavaTiles[1 * 5 + 3] = TileType.LAVA
        val lavaMap = TileMap(5, 3, lavaTiles)
        val openTiles = Array(15) { TileType.FLOOR }
        val openMap = TileMap(5, 3, openTiles)

        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val withLava = Raycaster.castColumns(lavaMap, pose, 32, 24, horizonY = 12f)
        val open = Raycaster.castColumns(openMap, pose, 32, 24, horizonY = 12f)

        val mid = withLava.size / 2
        // Лава прозрачна: центр-столбец проецируется как в полностью открытой комнате.
        assertEquals(open[mid].wallStart, withLava[mid].wallStart, 0.01f)
    }

    @Test
    fun `cast scene exposes wall texture metadata`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[2] = TileType.WALL
        val map = TileMap(3, 3, tiles)
        val scene = Raycaster.castScene(map, PlayerPose(1.5f, 1.5f, yaw = 0f), 16, 16, horizonY = 8f)
        assertTrue(scene.wallMeta.any { it.wallU >= 0f })
        assertEquals(scene.columns.size, scene.wallDistances.size)
        assertEquals(scene.columns.size, scene.wallMeta.size)
    }

    @Test
    fun `floor distance grows toward the horizon`() {
        val near = Raycaster.floorDistance(screenHeight = 200, horizon = 100f, screenRow = 190)
        val far = Raycaster.floorDistance(screenHeight = 200, horizon = 100f, screenRow = 110)
        assertTrue(far > near, "rows closer to horizon map to farther floor distances")
        assertTrue(Raycaster.floorDistance(200, 100f, 100).isInfinite(), "the horizon row is infinitely far")
    }

    @Test
    fun `ray direction points along yaw at screen center`() {
        // yaw=0 -> смотрим вдоль +X; центральный луч почти горизонтален (y~0).
        val ray = Raycaster.rayDirection(PlayerPose(0f, 0f, yaw = 0f), screenWidth = 64, col = 32)
        assertTrue(ray[0] > 0f)
        assertFalse(kotlin.math.abs(ray[1]) > 0.05f, "center ray should be roughly axis-aligned")
    }
}
