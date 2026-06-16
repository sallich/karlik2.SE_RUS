package ru.course.roguelike.policy.loop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.protocol.GameActions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.planner.ToolCallDecision

class PolicyContextCombatStallTest {
    @Test
    fun `combat stall triggers replan reason`() {
        val ctx = PolicyContext(combatStallSteps = 3, combatStallReplanCooldown = 0)
        val snap = combatRoom()
        repeat(4) {
            ctx.updateAfterStep(snap, snap, wait(), false)
        }
        assertTrue(ctx.isCombatStalled())
        assertEquals(ReplanReason.COMBAT_STALEMATE, ctx.shouldReplan(snap, isInitial = false))
    }

    private fun combatRoom(): GameSnapshot {
        val tiles = List(25) { TileType.FLOOR }
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = 5,
            height = 5,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
                hp = 50,
                maxHp = 100,
            ),
            tick = 0L,
            mobs = listOf(
                MobSnapshot(id = 1L, kind = MobKind.LLM_GUARD, x = 3.5f, y = 2.5f, hp = 50, maxHp = 50),
            ),
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 50_000, totalMs = 60_000),
        )
    }

    private fun wait() = ToolCallDecision(
        tool = "game_act",
        arguments = buildJsonObject { put("action", GameActions.WAIT) }.mapValues { it.value },
    )
}
