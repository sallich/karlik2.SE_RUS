package ru.course.roguelike.policy.observation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.llm.PolicyDeterministicPatch
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.loop.ReplanReason
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

class PolicyObservationTest {
    @Test
    fun `minimal patch enables door rules on door stuck without changing strategy`() {
        val snapshot = simpleFloor()
        val ctx = PolicyContext().also { it.initRunVariation(1L, nonce = 100L) }
        val obs = PolicyObservation.observe(snapshot, ctx).copy(
            failureSignals = setOf(FailureSignal.DOOR_E_NOT_READY),
            situation = PolicySituation.AT_DOOR_BLOCKED,
        )
        val patched = PolicyDeterministicPatch.apply(
            DefaultPolicies.standard(), obs, ReplanReason.DOOR_STUCK, ctx, snapshot,
        )
        val approach = patched.rules.first { it.whenClause == PolicyConditions.AT_DOOR_NEED_ENTER }
        assertTrue(approach.enabled)
        assertTrue(patched.notes?.contains("llm-unavailable-minimal-patch") == true)
    }

    private fun simpleFloor(): GameSnapshot = GameSnapshot(
        sessionId = "s1",
        seed = 1L,
        phase = SessionPhase.EXPLORATION.name,
        width = 3,
        height = 3,
        tiles = List(9) { TileType.FLOOR },
        player = PlayerSnapshot(
            pose = PlayerPose(1.5f, 1.5f, 0f, 0f),
            hp = 100,
            maxHp = 100,
        ),
        tick = 0L,
    )
}
