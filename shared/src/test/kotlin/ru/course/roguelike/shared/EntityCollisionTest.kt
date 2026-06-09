package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.model.WorldVertical

class EntityCollisionTest {
    @Test
    fun `circles overlap when closer than combined radius`() {
        val a = EntityCollision.Circle(0f, 0f, 0.5f)
        val b = EntityCollision.Circle(0.6f, 0f, 0.5f)
        assertTrue(EntityCollision.circlesOverlap(a, b))
    }

    @Test
    fun `circles do not overlap when far apart`() {
        val a = EntityCollision.Circle(0f, 0f, 0.3f)
        val b = EntityCollision.Circle(2f, 0f, 0.3f)
        assertFalse(EntityCollision.circlesOverlap(a, b))
    }

    @Test
    fun `ray hits circle in front of the player`() {
        val target = EntityCollision.Circle(3f, 0f, 0.35f)
        val hit = EntityCollision.raycastCircle(0f, 0f, yaw = 0f, maxDistance = 10f, target)
        assertNotNull(hit)
        assertTrue(hit!! in 2.5f..3.5f)
    }

    @Test
    fun `ray misses circle outside cone`() {
        val target = EntityCollision.Circle(0f, 3f, 0.35f)
        val hit = EntityCollision.raycastCircle(0f, 0f, yaw = 0f, maxDistance = 10f, target)
        assertNull(hit)
    }

    @Test
    fun `column blocks ground movement but not flying height`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.COLUMN
        val map = TileMap(3, 3, tiles)
        val onColumn = EntityCollision.Circle(1.5f, 1.5f, CombatConstants.MOB_RADIUS)
        assertTrue(EntityCollision.overlapsMovement(map, onColumn, localHeight = 0f))
        assertFalse(
            EntityCollision.overlapsMovement(
                map,
                onColumn,
                localHeight = WorldVertical.COLUMN_HEIGHT + 0.05f,
            ),
        )
    }

    @Test
    fun `room seal blocks player but mobs pass with passRoomSeals`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 1] = TileType.ROOM_SEAL
        val map = TileMap(3, 3, tiles)
        val onSeal = EntityCollision.Circle(1.5f, 1.5f, CombatConstants.MOB_RADIUS)
        assertTrue(EntityCollision.overlapsMovement(map, onSeal, localHeight = 0f))
        assertFalse(EntityCollision.overlapsMovement(map, onSeal, localHeight = 0f, passRoomSeals = true))
    }

    @Test
    fun `projectile circle blocked by wall tile`() {
        val tiles = Array(9) { TileType.FLOOR }
        tiles[1 * 3 + 2] = TileType.WALL
        val map = TileMap(3, 3, tiles)
        val insideWall = EntityCollision.Circle(2.5f, 1.5f, 0.1f)
        assertTrue(EntityCollision.overlapsWall(map, insideWall))
    }
}
