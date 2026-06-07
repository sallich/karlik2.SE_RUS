package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical
import ru.course.roguelike.shared.render.CameraProjection
import ru.course.roguelike.shared.render.Raycaster

class RaycasterTest {
    @Test
    fun `casts columns in enclosed room`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[0] = TileType.WALL
        tiles[2] = TileType.WALL
        val map = TileMap(3, 3, tiles)
        val cols = Raycaster.castColumns(map, PlayerPose(1.5f, 1.5f, 0f), 32, 24, pitchHorizonY = 12f)
        assertTrue(cols.any { it.wallEnd > it.wallStart + 1f })
    }

    @Test
    fun `walls shift down with elevation proportionally to distance`() {
        val pitchHorizon = 20f
        val screenHeight = 48
        val dist = 2f
        val groundedBottom = pitchHorizon + screenHeight * 0.5f / dist
        val grounded = Raycaster.projectWallSpan(
            pitchHorizonY = pitchHorizon,
            lineHeight = 10f,
            wallHeight = 1f,
            screenHeight = screenHeight,
            perpDistance = dist,
            viewerHeightAboveFloor = 0f,
        )
        assertEquals(groundedBottom, grounded.second, 0.01f)

        val near = Raycaster.projectWallSpan(
            pitchHorizonY = pitchHorizon,
            lineHeight = 10f,
            wallHeight = 1f,
            screenHeight = screenHeight,
            perpDistance = dist,
            viewerHeightAboveFloor = 0.5f,
        )
        val far = Raycaster.projectWallSpan(
            pitchHorizonY = pitchHorizon,
            lineHeight = 10f,
            wallHeight = 1f,
            screenHeight = screenHeight,
            perpDistance = 8f,
            viewerHeightAboveFloor = 0.5f,
        )
        assertTrue(near.second > grounded.second, "elevated near walls sit lower than grounded near")
        val groundedFar = Raycaster.projectWallSpan(
            pitchHorizonY = pitchHorizon,
            lineHeight = 10f,
            wallHeight = 1f,
            screenHeight = screenHeight,
            perpDistance = 8f,
            viewerHeightAboveFloor = 0f,
        )
        assertTrue(far.second > groundedFar.second, "elevated far walls sit lower than grounded far")
        assertTrue(near.second > far.second, "far walls shift less than near walls when elevated")
    }

    @Test
    fun `short column casts wall behind for upper screen span`() {
        val tiles = Array(15) { TileType.FLOOR }
        tiles[1 * 5 + 2] = TileType.COLUMN
        tiles[1 * 5 + 4] = TileType.WALL
        val map = TileMap(5, 3, tiles)
        val scene = Raycaster.castScene(map, PlayerPose(1.5f, 1.5f, yaw = 0f), 32, 24, pitchHorizonY = 12f)
        val col = scene.wallMeta[scene.wallMeta.size / 2]
        assertEquals(TileType.COLUMN, col.tile)
        assertEquals(TileType.WALL, col.backTile)
        assertTrue((col.backDistance ?: 0f) > (col.distance + 0.1f))
    }

    @Test
    fun `column blocks the ray at ground level`() {
        val tiles = Array(15) { TileType.FLOOR }
        tiles[1 * 5 + 3] = TileType.COLUMN
        val map = TileMap(5, 3, tiles)
        val cols = Raycaster.castColumns(map, PlayerPose(1.5f, 1.5f, yaw = 0f), 32, 24, pitchHorizonY = 12f)
        val center = cols[cols.size / 2]
        assertTrue(center.wallEnd > center.wallStart + 1f, "column should project a solid vertical span")
    }

    @Test
    fun `jump beside column keeps face visible until feet clear the top`() {
        val tiles = Array(15) { TileType.FLOOR }
        tiles[1 * 5 + 3] = TileType.COLUMN
        val map = TileMap(5, 3, tiles)
        val grounded = Raycaster.castColumns(
            map,
            PlayerPose(1.5f, 1.5f, yaw = 0f, height = 0f),
            32,
            24,
            pitchHorizonY = 12f,
        )
        val jumping = Raycaster.castColumns(
            map,
            PlayerPose(1.5f, 1.5f, yaw = 0f, height = WorldVertical.MAX_JUMP_CLEARANCE),
            32,
            24,
            pitchHorizonY = 12f,
        )
        val cleared = Raycaster.castColumns(
            map,
            PlayerPose(1.5f, 1.5f, yaw = 0f, height = WorldVertical.COLUMN_HEIGHT + 0.05f),
            32,
            24,
            pitchHorizonY = 12f,
        )
        val mid = grounded.size / 2
        val groundedSpan = grounded[mid].wallEnd - grounded[mid].wallStart
        val jumpSpan = jumping[mid].wallEnd - jumping[mid].wallStart
        val clearedSpan = cleared[mid].wallEnd - cleared[mid].wallStart
        assertTrue(groundedSpan > 1f)
        assertTrue(jumpSpan > 1f, "partial jump still intersects the column face")
        assertTrue(clearedSpan < groundedSpan, "feet above column top clear the ray")
    }

    @Test
    fun `lava does not block the ray`() {
        val lavaTiles = Array(15) { TileType.FLOOR }
        lavaTiles[1 * 5 + 3] = TileType.LAVA
        val lavaMap = TileMap(5, 3, lavaTiles)
        val openTiles = Array(15) { TileType.FLOOR }
        val openMap = TileMap(5, 3, openTiles)

        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f)
        val withLava = Raycaster.castColumns(lavaMap, pose, 32, 24, pitchHorizonY = 12f)
        val open = Raycaster.castColumns(openMap, pose, 32, 24, pitchHorizonY = 12f)

        val mid = withLava.size / 2
        assertEquals(open[mid].wallStart, withLava[mid].wallStart, 0.01f)
    }

    @Test
    fun `cast scene exposes wall texture metadata`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[2] = TileType.WALL
        val map = TileMap(3, 3, tiles)
        val scene = Raycaster.castScene(map, PlayerPose(1.5f, 1.5f, yaw = 0f), 16, 16, pitchHorizonY = 8f)
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
    fun `floor distance accounts for local height`() {
        val grounded = Raycaster.floorDistance(screenHeight = 200, horizon = 100f, screenRow = 150, localHeight = 0f)
        val elevated = Raycaster.floorDistance(screenHeight = 200, horizon = 100f, screenRow = 150, localHeight = 0.5f)
        assertTrue(elevated > grounded, "elevated camera samples farther floor at the same screen row")
    }

    @Test
    fun `ray direction points along yaw at screen center`() {
        val ray = Raycaster.rayDirection(PlayerPose(0f, 0f, yaw = 0f), screenWidth = 64, col = 32)
        assertTrue(ray[0] > 0f)
        assertFalse(kotlin.math.abs(ray[1]) > 0.05f, "center ray should be roughly axis-aligned")
    }
}
