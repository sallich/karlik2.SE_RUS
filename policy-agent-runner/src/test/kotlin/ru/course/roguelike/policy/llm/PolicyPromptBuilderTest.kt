package ru.course.roguelike.policy.llm

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType

class PolicyPromptBuilderTest {
    @Test
    fun `initial prompt does not force a strategy roll`() {
        val snapshot = simpleFloor()
        val context = PolicyContext().also { it.initRunVariation(snapshot.seed, nonce = 42L) }
        val message = PolicyPromptBuilder.initialUserMessage(snapshot, context)
        assertFalse(message.contains("REQUIRED strategy roll"), message)
        assertFalse(message.contains("You MUST set phase"), message)
        assertTrue(message.contains("YOU decide"), message)
    }

    @Test
    fun `replan context is factual not prescriptive`() {
        val snapshot = simpleFloor()
        val context = PolicyContext().also { it.initRunVariation(snapshot.seed) }
        val ctx = PolicySnapshotBrief.replanContext("STUCK", snapshot, context)
        assertTrue(ctx.contains("trigger=STUCK"))
        assertFalse(ctx.contains("Enable stuck"), ctx)
        assertFalse(ctx.contains("params.unstuckMode="), ctx)
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
