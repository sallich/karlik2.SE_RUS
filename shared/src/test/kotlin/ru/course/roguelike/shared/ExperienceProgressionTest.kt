package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.MobKind

class ExperienceProgressionTest {
    @Test
    fun `starting level has base stats`() {
        assertEquals(1, ExperienceProgression.STARTING_LEVEL)
        assertEquals(100, ExperienceProgression.maxHpForLevel(1))
        assertEquals(CombatConstants.PLAYER_ATTACK_DAMAGE, ExperienceProgression.attackDamageForLevel(1))
    }

    @Test
    fun `stats scale with level`() {
        assertEquals(120, ExperienceProgression.maxHpForLevel(2))
        assertEquals(140, ExperienceProgression.maxHpForLevel(3))
        assertEquals(30, ExperienceProgression.attackDamageForLevel(2))
        assertEquals(35, ExperienceProgression.attackDamageForLevel(3))
    }

    @Test
    fun `xp thresholds grow per level`() {
        assertEquals(100, ExperienceProgression.xpRequiredForNextLevel(1))
        assertEquals(150, ExperienceProgression.xpRequiredForNextLevel(2))
        assertEquals(200, ExperienceProgression.xpRequiredForNextLevel(3))
    }

    @Test
    fun `total xp for level matches cumulative thresholds`() {
        assertEquals(0, ExperienceProgression.totalXpForLevel(1))
        assertEquals(100, ExperienceProgression.totalXpForLevel(2))
        assertEquals(250, ExperienceProgression.totalXpForLevel(3))
    }

    @Test
    fun `level is derived from total xp`() {
        assertEquals(1, ExperienceProgression.levelFromTotalXp(0))
        assertEquals(1, ExperienceProgression.levelFromTotalXp(99))
        assertEquals(2, ExperienceProgression.levelFromTotalXp(100))
        assertEquals(2, ExperienceProgression.levelFromTotalXp(249))
        assertEquals(3, ExperienceProgression.levelFromTotalXp(250))
    }

    @Test
    fun `xp progress reports in-level progress`() {
        assertEquals(0 to 100, ExperienceProgression.xpProgressInLevel(0))
        assertEquals(50 to 100, ExperienceProgression.xpProgressInLevel(50))
        assertEquals(0 to 150, ExperienceProgression.xpProgressInLevel(100))
        assertEquals(75 to 150, ExperienceProgression.xpProgressInLevel(175))
    }

    @Test
    fun `mob kill xp depends on kind`() {
        assertEquals(ExperienceProgression.MELEE_MOB_XP, ExperienceProgression.mobKillXp(MobKind.MELEE))
        assertEquals(ExperienceProgression.RANGED_MOB_XP, ExperienceProgression.mobKillXp(MobKind.RANGED))
        assertTrue(ExperienceProgression.mobKillXp(MobKind.RANGED) > ExperienceProgression.mobKillXp(MobKind.MELEE))
    }
}
