package ru.course.roguelike.shared.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.PlayerPose

class CombatAimTest {
    @Test
    fun `pitch aims up at flying mob`() {
        val pose = PlayerPose(2.5f, 2.5f, yaw = 0f)
        val pitch = CombatAim.pitchToTarget(
            pose,
            targetX = 4.5f,
            targetY = 2.5f,
            targetZ = CombatAim.mobHitCenterZ(CombatConstants.FLYING_MOB_Z),
        )
        assertTrue(pitch > 0.1f, "expected upward pitch, got $pitch")
    }

    @Test
    fun `pitch aims down at ground mob`() {
        val pose = PlayerPose(2.5f, 2.5f, yaw = 0f)
        val pitch = CombatAim.pitchToTarget(
            pose,
            targetX = 3.5f,
            targetY = 2.5f,
            targetZ = CombatAim.mobHitCenterZ(0f),
        )
        assertTrue(pitch < 0f, "expected downward pitch for ground target, got $pitch")
    }

    @Test
    fun `mob hit center includes half height`() {
        assertEquals(
            CombatConstants.FLYING_MOB_Z + CombatConstants.MOB_HIT_HALF_HEIGHT,
            CombatAim.mobHitCenterZ(CombatConstants.FLYING_MOB_Z),
        )
    }
}
