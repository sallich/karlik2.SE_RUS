package ru.course.roguelike.policy.planner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot

class PolicyDoorPlannerTest {
    @Test
    fun `approach door uses game_sync`() {
        val snapshot = corridorWithDoor()
        val knowledge = PlayerKnowledgeLayer().also { it.revealAllForTest(snapshot) }
        val decision = PolicyDoorPlanner.plan("s1", snapshot, knowledge)
        assertEquals("game_sync", decision.tool)
    }

    private fun corridorWithDoor(): GameSnapshot {
        val w = 7
        val h = 5
        val tiles = Array(w * h) { TileType.WALL }
        for (x in 1 until w - 1) {
            for (y in 1 until h - 1) {
                tiles[y * w + x] = TileType.FLOOR
            }
        }
        tiles[2 * w + 6] = TileType.ROOM_SEAL
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles.toList(),
            player = PlayerSnapshot(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
                ammo = 12,
                maxAmmo = 12,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 3,
            doorMarkers = listOf(
                DoorMarkerSnapshot(x = 6.5f, y = 2.5f, mobRoom = true),
            ),
        )
    }
}
