package ru.course.roguelike.game.domain.progression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.combat.MobSpawner
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class ProgressionSystemTest {
    private fun emptySession(): GameSession {
        val tiles = Array(9) { TileType.FLOOR }
        return GameSession(
            sessionId = "progression",
            seed = 1L,
            map = TileMap(3, 3, tiles),
            playerPose = PlayerPose(1.5f, 1.5f, yaw = 0f),
        )
    }

    @Test
    fun `mob kill awards xp by kind`() {
        val session = emptySession()

        val events = ProgressionSystem.awardMobKill(session, MobKind.MELEE)

        assertEquals(ExperienceProgression.MELEE_MOB_XP, session.playerExperience)
        assertTrue(events.any { it is GameEvent.ExperienceGained })
    }

    @Test
    fun `enough xp triggers level up with increased hp and damage`() {
        val session = emptySession()
        val xpForLevel2 = ExperienceProgression.totalXpForLevel(2)
        val killsNeeded = (xpForLevel2 + ExperienceProgression.RANGED_MOB_XP - 1) /
            ExperienceProgression.RANGED_MOB_XP

        repeat(killsNeeded) {
            ProgressionSystem.awardMobKill(session, MobKind.RANGED)
        }

        assertEquals(2, session.playerLevel)
        assertEquals(ExperienceProgression.maxHpForLevel(2), session.playerMaxHp)
        assertEquals(ExperienceProgression.attackDamageForLevel(2), session.playerAttackDamage)
        assertEquals(ExperienceProgression.maxHpForLevel(2), session.playerHp)
    }

    @Test
    fun `location completion awards bonus xp once`() {
        val session = emptySession()

        val first = ProgressionSystem.checkLocationCompletion(session)
        val second = ProgressionSystem.checkLocationCompletion(session)

        assertEquals(ExperienceProgression.LOCATION_COMPLETION_XP, session.playerExperience)
        assertTrue(first.any { it is GameEvent.LocationCompleted })
        assertTrue(second.isEmpty())
    }

    @Test
    fun `location completion is skipped while mobs remain`() {
        val session = emptySession()
        session.mobs.add(MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 1.5f))

        val events = ProgressionSystem.checkLocationCompletion(session)

        assertTrue(events.isEmpty())
        assertEquals(0, session.playerExperience)
    }

    @Test
    fun `snapshot exposes in-level xp progress for the hud`() {
        val session = emptySession()
        ProgressionSystem.awardMobKill(session, MobKind.MELEE)

        val snap = session.toSnapshot()

        assertEquals(1, snap.player.level)
        assertEquals(ExperienceProgression.MELEE_MOB_XP, snap.player.experience)
        assertEquals(ExperienceProgression.xpRequiredForNextLevel(1), snap.player.experienceToNextLevel)
        assertEquals(ExperienceProgression.attackDamageForLevel(1), snap.player.attackDamage)
    }
}
