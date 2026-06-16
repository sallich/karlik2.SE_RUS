package ru.course.roguelike.policy.loop

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

class PolicyContextLoopTest {
    @Test
    fun `loop escape is urgent replan only`() {
        val ctx = PolicyContext()
        assertTrue(ctx.isUrgentReplan(ReplanReason.LOOP_ESCAPE))
        assertFalse(ctx.isUrgentReplan(ReplanReason.STUCK))
        assertFalse(ctx.isUrgentReplan(ReplanReason.DOOR_STUCK))
    }

    @Test
    fun `steady objective pursuit while moving is not a loop`() {
        val ctx = PolicyContext()
        listOf(1, 2, 3, 4, 5, 6).forEach { x ->
            ctx.recordActionTrace("objective:explore", snapshotAt(x, 4))
        }
        assertFalse(ctx.needsLoopEscape())
    }

    @Test
    fun `needs loop escape after repeated same guard`() {
        val ctx = PolicyContext()
        val snap = snapshotAt(4, 4)
        repeat(8) {
            ctx.recordActionTrace("at_door_need_enter", snap)
        }
        assertTrue(ctx.needsLoopEscape())
        assertTrue(ctx.shouldReplan(snap, isInitial = false) == ReplanReason.LOOP_ESCAPE)
    }

    @Test
    fun `sync replan clears door commitments`() {
        val ctx = PolicyContext()
        ctx.committedApproachCell = ru.course.roguelike.shared.model.GridPos(3, 4)
        ctx.committedDoorCell = ru.course.roguelike.shared.model.GridPos(4, 4)
        ctx.setExplorationTarget("1,1")
        ctx.onSyncReplanApplied(ReplanReason.LOOP_ESCAPE)
        assertTrue(ctx.committedApproachCell == null)
        assertTrue(ctx.committedDoorCell == null)
        assertTrue(ctx.explorationTargetKey == null)
    }

    private fun snapshotAt(x: Int, y: Int): GameSnapshot = GameSnapshot(
        sessionId = "t",
        seed = 1L,
        phase = SessionPhase.EXPLORATION.name,
        width = 10,
        height = 10,
        tiles = List(100) { TileType.FLOOR },
        player = PlayerSnapshot(
            pose = PlayerPose(x + 0.5f, y + 0.5f, 0f, 0f),
            hp = 100,
            maxHp = 100,
        ),
        tick = 0L,
        keysCollected = 0,
        keysRequired = 2,
    )
}
