package ru.course.roguelike.policy.dsl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.knowledge.PartialTileMap
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

class PolicyExploreHelperTest {
    private fun corridor(): TileMap {
        // 8x3 map, single floor corridor along row y=1, x=1..6.
        val w = 8
        val h = 3
        val tiles = MutableList(w * h) { TileType.WALL }
        for (x in 1..6) tiles[1 * w + x] = TileType.FLOOR
        return TileMap.fromFlat(w, h, tiles)
    }

    @Test
    fun `from dead end steps back toward nearest unvisited cell`() {
        val map = corridor()
        // Standing at the east dead end (6,1); already stood on 6,5,4.
        val visited = setOf("6,1", "5,1", "4,1")
        val next = PolicyExploreHelper.stepTowardNearestUnvisited(map, GridPos(6, 1), visited)
        // Must move west toward the unvisited stretch, i.e. to (5,1) — never bounce back east.
        assertEquals(GridPos(5, 1), next)
    }

    @Test
    fun `does not oscillate - target stays consistent across a step`() {
        val map = corridor()
        // At (5,1): nearest unvisited is (3,1) via 4; step should be (4,1).
        val visited1 = setOf("6,1", "5,1")
        val step1 = PolicyExploreHelper.stepTowardNearestUnvisited(map, GridPos(5, 1), visited1)
        assertEquals(GridPos(4, 1), step1)
        // After moving to (4,1) it becomes visited; from (4,1) the next step is (3,1), not back to (5,1).
        val visited2 = visited1 + "4,1"
        val step2 = PolicyExploreHelper.stepTowardNearestUnvisited(map, GridPos(4, 1), visited2)
        assertEquals(GridPos(3, 1), step2)
    }

    @Test
    fun `fully visited corridor yields no unvisited target`() {
        val map = corridor()
        val visited = (1..6).map { "$it,1" }.toSet()
        val next = PolicyExploreHelper.stepTowardNearestUnvisited(map, GridPos(3, 1), visited)
        assertNull(next)
    }

    @Test
    fun `picks adjacent unvisited cell when available`() {
        val map = corridor()
        val visited = setOf("3,1")
        val next = PolicyExploreHelper.stepTowardNearestUnvisited(map, GridPos(3, 1), visited)
        // Either neighbor (2,1) or (4,1) is fine, as long as it is adjacent and unvisited.
        val key = next?.let { PartialTileMap.cellKey(it) }
        assert(key == "2,1" || key == "4,1") { "expected adjacent unvisited, got $key" }
    }

    @Test
    fun `committed exploration target persists across steps (no mid-corridor flip)`() {
        // 12x3 long corridor x=1..10 at y=1.
        val w = 12
        val h = 3
        val tiles = MutableList(w * h) { TileType.WALL }
        for (x in 1..10) tiles[1 * w + x] = TileType.FLOOR

        fun snapAt(x: Int) = GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = w,
            height = h,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = x + 0.5f, y = 1.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
        )

        val ctx = PolicyContext(strictFairPlay = false)
        // Stand at the center; mark everything visited except the two ends so both directions
        // offer an unvisited goal at equal distance — the classic flip-flop trap.
        ctx.knowledge.revealAllForTest(snapAt(5))
        for (x in 2..9) ctx.visitedCells.add("$x,1")

        val first = PolicyExploreHelper.moveTowardUnvisited(
            snapshot = snapAt(5),
            sessionId = "s1",
            visited = ctx.visitedCells,
            knowledge = ctx.knowledge,
            stepIndex = 0,
            lastBlockedMove = null,
            strictFairPlay = false,
            context = ctx,
        )
        assertEquals("game_sync", first.tool)
        val committed = ctx.explorationTargetKey
        assertNotNull(committed, "explore should commit to a target")

        // Simulate moving one cell toward the target and recompute: the committed target must not
        // flip to the opposite end of the corridor.
        val committedCell = committed!!.split(",").let { GridPos(it[0].toInt(), it[1].toInt()) }
        val nextX = if (committedCell.x > 5) 6 else 4
        ctx.visitedCells.add("$nextX,1")
        PolicyExploreHelper.moveTowardUnvisited(
            snapshot = snapAt(nextX),
            sessionId = "s1",
            visited = ctx.visitedCells,
            knowledge = ctx.knowledge,
            stepIndex = 1,
            lastBlockedMove = null,
            strictFairPlay = false,
            context = ctx,
        )
        assertEquals(committed, ctx.explorationTargetKey, "committed target must stay stable, not flip")
    }
}
