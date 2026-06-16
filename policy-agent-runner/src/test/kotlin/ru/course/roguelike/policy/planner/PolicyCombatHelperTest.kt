package ru.course.roguelike.policy.planner

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

class PolicyCombatHelperTest {
    @Test
    fun `reload in mob room backs away while facing mob when melee is close`() {
        val snap = combatRoomSnapshot(ammo = 0, hp = 1, kind = MobKind.MELEE)
        val decision = PolicyCombatHelper.reloadDecision("s1", snap)
        assertEquals("game_sync", decision.tool)
        assertTrue(decision.arguments["reload"]?.toString()?.contains("true") == true)
        assertTrue(decision.arguments["backward"]?.toString()?.contains("true") == true)
        assertTrue(decision.arguments.containsKey("clientYaw"))
    }

    @Test
    fun `boss fight plants feet and shoots without strafe`() {
        val snap = combatRoomSnapshot(ammo = 6, hp = 50, kind = MobKind.LLM_GUARD)
        val decision = PolicyCombatHelper.combatDecision("s1", snap)
        assertEquals("game_sync", decision.tool)
        assertTrue(decision.arguments["attack"]?.toString()?.contains("true") == true)
        assertTrue(!decision.arguments.containsKey("forward"))
        assertTrue(!decision.arguments.containsKey("strafeLeft"))
    }

    @Test
    fun `chase combat style pushes forward while firing`() {
        val snap = combatRoomSnapshot(ammo = 6, hp = 50, kind = MobKind.LLM_GUARD)
        val decision = PolicyCombatHelper.combatDecision("s1", snap, ru.course.roguelike.policy.dsl.PolicyParams.COMBAT_CHASE)
        assertTrue(decision.arguments["forward"]?.toString()?.contains("true") == true)
        assertTrue(decision.arguments["attack"]?.toString()?.contains("true") == true)
    }

    @Test
    fun `reload vs ranged boss does not backpedal`() {
        val snap = combatRoomSnapshot(ammo = 0, hp = 50, kind = MobKind.LLM_GUARD)
        val decision = PolicyCombatHelper.reloadDecision("s1", snap)
        assertTrue(decision.arguments["reload"]?.toString()?.contains("true") == true)
        assertTrue(!decision.arguments.containsKey("backward"))
    }

    private fun combatRoomSnapshot(ammo: Int, hp: Int, kind: MobKind = MobKind.MELEE): GameSnapshot {
        val width = 5
        val height = 5
        val tiles = List(width * height) { TileType.FLOOR }
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = width,
            height = height,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
                hp = hp,
                maxHp = 100,
                ammo = ammo,
                maxAmmo = 12,
            ),
            tick = 0L,
            mobs = listOf(
                MobSnapshot(id = 1L, kind = kind, x = 3.5f, y = 2.5f, hp = 50, maxHp = 50),
            ),
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 50_000, totalMs = 60_000),
        )
    }
}
