package ru.course.roguelike.shared.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

class HorizontalSurfacePickerTest {
    @Test
    fun `elevated viewer picks column top ahead`() {
        val tiles = Array(15) { TileType.FLOOR }
        tiles[1 * 5 + 2] = TileType.COLUMN
        val map = TileMap(5, 3, tiles)
        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f, height = WorldVertical.COLUMN_HEIGHT)
        val ray = Raycaster.rayDirection(pose, screenWidth = 64, col = 32)
        val hit = HorizontalSurfacePicker.pick(
            map,
            pose,
            ray,
            screenHeight = 200,
            horizon = 100f,
            screenRow = 199,
            viewerHeight = WorldVertical.COLUMN_HEIGHT,
        )
        assertNotNull(hit)
        assertEquals(TileType.COLUMN, hit!!.tile)
        assertEquals(WorldVertical.COLUMN_HEIGHT, hit.surfaceZ, 0.01f)
    }

    @Test
    fun `high viewer picks wall top ahead`() {
        val tiles = Array(15) { TileType.FLOOR }
        tiles[1 * 5 + 4] = TileType.WALL
        val map = TileMap(5, 3, tiles)
        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f, height = 2f)
        val ray = Raycaster.rayDirection(pose, screenWidth = 64, col = 32)
        val hit = HorizontalSurfacePicker.pick(
            map,
            pose,
            ray,
            screenHeight = 200,
            horizon = 100f,
            screenRow = 199,
            viewerHeight = 2f,
        )
        assertNotNull(hit)
        assertEquals(TileType.WALL, hit!!.tile)
        assertEquals(WorldVertical.WALL_HEIGHT, hit.surfaceZ, 0.01f)
    }
}
