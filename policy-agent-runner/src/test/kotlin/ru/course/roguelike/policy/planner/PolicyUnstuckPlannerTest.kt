package ru.course.roguelike.policy.planner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.dsl.PolicyInterpreter
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.protocol.GameActions

class PolicyUnstuckPlannerTest {
    @Test
    fun `unstuck uses game_sync not compass move_west`() {
        val snapshot = corridorAgainstWall(yaw = 1.2f)
        val knowledge = knowledgeFor(snapshot)
        val decision = PolicyUnstuckPlanner.plan(
            sessionId = "s1",
            snapshot = snapshot,
            knowledge = knowledge,
            stepIndex = 3,
            lastBlockedMove = GameActions.MOVE_WEST,
            avoidCellKey = null,
        )
        assertEquals("game_sync", decision.tool)
    }

    @Test
    fun `unstuck avoids ping pong backtrack cell`() {
        val snapshot = corridorSnapshot()
        val knowledge = knowledgeFor(snapshot)
        val map = knowledge.navigableMap(snapshot)
        val cell = GridPos(2, 1)
        val target = PolicyUnstuckPlanner.pickTargetCell(
            snapshot = snapshot,
            knowledge = knowledge,
            map = map,
            cell = cell,
            stepIndex = 0,
            lastBlockedMove = GameActions.MOVE_EAST,
            avoidCellKey = "1,1",
        )
        assertTrue(target != GridPos(1, 1))
    }

    @Test
    fun `stuck rule wins over needs keys when ping pong`() {
        val snapshot = corridorSnapshot()
        val ctx = PolicyContext()
        ctx.knowledge.revealAllForTest(snapshot)
        ctx.updateAfterStep(snapshotAt(snapshot, 1, 1), snapshotAt(snapshot, 2, 1), act(GameActions.MOVE_EAST), false)
        ctx.updateAfterStep(snapshotAt(snapshot, 2, 1), snapshotAt(snapshot, 1, 1), act(GameActions.MOVE_WEST), false)
        ctx.updateAfterStep(snapshotAt(snapshot, 1, 1), snapshotAt(snapshot, 2, 1), act(GameActions.MOVE_EAST), false)
        ctx.updateAfterStep(snapshotAt(snapshot, 2, 1), snapshotAt(snapshot, 1, 1), act(GameActions.MOVE_WEST), false)
        assertTrue(ctx.isTrapped())
        assertTrue(!PolicyInterpreter.matches(PolicyConditions.NEEDS_KEYS, snapshotAt(snapshot, 1, 1), ctx))
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshotAt(snapshot, 1, 1), "s1", ctx)
        assertEquals(PolicyConditions.STUCK, result.condition)
        assertEquals("game_sync", result.decision.tool)
    }

    @Test
    fun `failed game_sync rotates unstuck target`() {
        val snapshot = corridorSnapshot()
        val knowledge = knowledgeFor(snapshot)
        val map = knowledge.navigableMap(snapshot)
        val cell = GridPos(1, 1)
        val first = PolicyUnstuckPlanner.pickTargetCell(
            snapshot = snapshot,
            knowledge = knowledge,
            map = map,
            cell = cell,
            stepIndex = 0,
            lastBlockedMove = null,
            avoidCellKey = null,
            avoidTargetKeys = emptySet(),
            stuckAttempt = 0,
        )
        val second = PolicyUnstuckPlanner.pickTargetCell(
            snapshot = snapshot,
            knowledge = knowledge,
            map = map,
            cell = cell,
            stepIndex = 1,
            lastBlockedMove = null,
            avoidCellKey = null,
            avoidTargetKeys = setOf("${first.x},${first.y}"),
            stuckAttempt = 1,
        )
        assertTrue(first != second || walkableCount(map, cell) <= 1)
    }

    private fun knowledgeFor(snapshot: GameSnapshot): PlayerKnowledgeLayer =
        PlayerKnowledgeLayer().also { it.revealAllForTest(snapshot) }

    private fun walkableCount(map: TileMap, cell: GridPos): Int =
        listOf(
            GridPos(cell.x + 1, cell.y),
            GridPos(cell.x - 1, cell.y),
            GridPos(cell.x, cell.y + 1),
            GridPos(cell.x, cell.y - 1),
        ).count { map.get(it)?.walkable == true }

    private fun corridorAgainstWall(yaw: Float): GameSnapshot {
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
                ammo = 12,
                maxAmmo = 12,
                pose = PlayerPose(x = 2.5f, y = 1.5f, yaw = yaw, pitch = 0f),
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 3,
            doorMarkers = emptyList(),
            mobs = emptyList(),
            keyPickups = emptyList(),
        )
    }

    private fun corridorSnapshot(): GameSnapshot = corridorAgainstWall(yaw = 0f)

    private fun snapshotAt(base: GameSnapshot, x: Int, y: Int): GameSnapshot =
        base.copy(
            player = base.player.copy(
                pose = base.player.pose.copy(x = x + 0.5f, y = y + 0.5f),
            ),
        )

    private fun act(action: String) = ru.course.roguelike.agent.planner.ToolCallDecision(
        tool = "game_act",
        arguments = mapOf(
            "sessionId" to kotlinx.serialization.json.JsonPrimitive("s1"),
            "action" to kotlinx.serialization.json.JsonPrimitive(action),
        ),
    )
}
