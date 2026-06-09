package ru.course.roguelike.shared.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class DoorInteractionTest {
    @Test
    fun `finds door from adjacent corridor tile`() {
        val map = TileMap(5, 3, Array(15) { TileType.WALL }.also { tiles ->
            tiles[1 * 5 + 1] = TileType.FLOOR
            tiles[1 * 5 + 2] = TileType.ROOM_DOOR
            tiles[1 * 5 + 3] = TileType.FLOOR
        })
        val pose = PlayerPose(3.5f, 1.5f, yaw = Math.PI.toFloat())
        assertEquals(GridPos(2, 1), DoorInteraction.findInteractable(map, pose))
    }

    @Test
    fun `finds door in view ray`() {
        val map = TileMap(5, 3, Array(15) { TileType.WALL }.also { tiles ->
            tiles[1 * 5 + 1] = TileType.FLOOR
            tiles[1 * 5 + 2] = TileType.ROOM_DOOR
        })
        val pose = PlayerPose(1.5f, 1.5f, yaw = 0f)
        assertNotNull(DoorInteraction.findInView(map, pose))
    }
}
