package ru.course.roguelike.policy.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.engine.GridPathfinder
import ru.course.roguelike.shared.model.GridPos

class PlayerKnowledgeLayerTest {
    @Test
    fun `accumulates known cells from visibility radius`() {
        val snapshot = smallMap()
        val layer = PlayerKnowledgeLayer()
        layer.update(snapshot, setOf("2,1"))
        assertTrue("2,1" in layer.knownCells)
        assertTrue("3,1" in layer.knownCells)
        assertTrue(layer.knownCells.size in 6..15)
    }

    @Test
    fun `partial map blocks path through unknown`() {
        val snapshot = wideMap()
        val layer = PlayerKnowledgeLayer()
        layer.update(snapshot, setOf("0,1"))
        val partial = layer.navigableMap(snapshot)
        val path = GridPathfinder.path(partial, GridPos(0, 1), GridPos(9, 1))
        assertTrue(path == null)
    }

    @Test
    fun `brief uses known doors only`() {
        val snapshot = smallMap()
        val layer = PlayerKnowledgeLayer()
        layer.update(snapshot, setOf("2,1"))
        val brief = layer.toBrief(snapshot)
        assertTrue(brief.contains("knownDoors="))
        assertTrue(brief.contains("localMap"))
    }

    @Test
    fun `frontier cells border unknown`() {
        val snapshot = smallMap()
        val layer = PlayerKnowledgeLayer()
        layer.revealAllForTest(snapshot)
        layer.knownCells.remove("4,1")
        val frontier = layer.frontierCells(snapshot)
        assertTrue(frontier.any { it.x == 3 && it.y == 1 })
    }

    @Test
    fun `frontier excludes lava cells`() {
        val w = 5
        val h = 3
        val tiles = Array(w * h) { TileType.WALL }
        tiles[1 * w + 2] = TileType.LAVA
        tiles[1 * w + 3] = TileType.FLOOR
        val snapshot = GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles.toList(),
            player = PlayerSnapshot(
                hp = 100,
                maxHp = 100,
                pose = PlayerPose(x = 0.5f, y = 1.5f, yaw = 0f, pitch = 0f),
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
        )
        val layer = PlayerKnowledgeLayer()
        layer.revealAllForTest(snapshot)
        layer.knownCells.remove("4,1")
        val frontier = layer.frontierCells(snapshot)
        assertFalse(frontier.any { it.x == 2 && it.y == 1 }, "lava must not be a frontier anchor")
    }

    @Test
    fun `brief includes lava legend and known lava`() {
        val w = 5
        val h = 3
        val tiles = Array(w * h) { TileType.WALL }
        tiles[1 * w + 2] = TileType.LAVA
        for (x in 0 until w) if (x != 2) tiles[1 * w + x] = TileType.FLOOR
        val snapshot = GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles.toList(),
            player = PlayerSnapshot(
                hp = 80,
                maxHp = 100,
                pose = PlayerPose(x = 1.5f, y = 1.5f, yaw = 0f, pitch = 0f),
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
        )
        val layer = PlayerKnowledgeLayer()
        layer.update(snapshot, setOf("1,1"))
        val brief = layer.toBrief(snapshot)
        assertTrue(brief.contains("L=LAVA"))
        assertTrue(brief.contains("knownLava=(2,1)"))
        assertTrue(brief.contains("hp=80/100"))
    }

    private fun smallMap(): GameSnapshot {
        val w = 5
        val h = 3
        val tiles = Array(w * h) { TileType.WALL }
        for (x in 0 until w) {
            tiles[1 * w + x] = TileType.FLOOR
        }
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles.toList(),
            player = PlayerSnapshot(
                hp = 100,
                maxHp = 100,
                pose = PlayerPose(x = 2.5f, y = 1.5f, yaw = 0f, pitch = 0f),
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
            doorMarkers = emptyList(),
        )
    }

    private fun wideMap(): GameSnapshot {
        val w = 10
        val h = 3
        val tiles = Array(w * h) { TileType.WALL }
        for (x in 0 until w) {
            tiles[1 * w + x] = TileType.FLOOR
        }
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles.toList(),
            player = PlayerSnapshot(
                hp = 100,
                maxHp = 100,
                pose = PlayerPose(x = 0.5f, y = 1.5f, yaw = 0f, pitch = 0f),
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
            doorMarkers = emptyList(),
        )
    }
}
