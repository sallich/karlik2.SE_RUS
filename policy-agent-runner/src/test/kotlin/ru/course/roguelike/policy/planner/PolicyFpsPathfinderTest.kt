package ru.course.roguelike.policy.planner

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

class PolicyFpsPathfinderTest {
    @Test
    fun `cannot traverse into column cell`() {
        val w = 5
        val tiles = MutableList(w * w) { TileType.FLOOR }
        tiles[2 * w + 2] = TileType.COLUMN
        val map = TileMap.fromFlat(w, w, tiles)
        assertFalse(PolicyFpsPathfinder.canTraverse(map, GridPos(2, 1), GridPos(2, 2)))
    }

    @Test
    fun `fps path routes around column choke`() {
        val w = 7
        val h = 5
        val tiles = MutableList(w * h) { TileType.WALL }
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                tiles[y * w + x] = TileType.FLOOR
            }
        }
        tiles[2 * w + 3] = TileType.COLUMN
        tiles[2 * w + 4] = TileType.COLUMN
        val map = TileMap.fromFlat(w, h, tiles)
        val path = PolicyFpsPathfinder.path(map, GridPos(1, 2), GridPos(5, 2))
        assertNotNull(path)
    }

    @Test
    fun `vertical mode can step onto a column but ground mode cannot`() {
        val w = 5
        val tiles = MutableList(w * w) { TileType.FLOOR }
        tiles[2 * w + 2] = TileType.COLUMN
        val map = TileMap.fromFlat(w, w, tiles)
        assertFalse(PolicyFpsPathfinder.canTraverse(map, GridPos(2, 1), GridPos(2, 2)))
        assertTrue(PolicyFpsPathfinder.canTraverse(map, GridPos(2, 1), GridPos(2, 2), allowVertical = true))
    }

    @Test
    fun `vertical path climbs over a column that fully blocks a one-wide corridor`() {
        val w = 7
        val h = 3
        val tiles = MutableList(w * h) { TileType.WALL }
        for (x in 1..5) tiles[1 * w + x] = TileType.FLOOR
        tiles[1 * w + 3] = TileType.COLUMN
        val map = TileMap.fromFlat(w, h, tiles)
        assertNull(PolicyFpsPathfinder.path(map, GridPos(1, 1), GridPos(5, 1)))
        val vertical = PolicyFpsPathfinder.path(map, GridPos(1, 1), GridPos(5, 1), allowVertical = true)
        assertNotNull(vertical)
        assertTrue(vertical!!.contains(GridPos(3, 1)))
    }
}
