package ru.course.roguelike.game.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

class GameEngineTest {
    @Test
    fun `createSession produces a labyrinth snapshot with full hp in exploration`() {
        val engine = GameEngine()
        val snap = engine.createSession(seed = 123L)

        assertEquals(SessionPhase.EXPLORATION.name, snap.phase)
        assertEquals(123L, snap.seed)
        assertEquals(100, snap.player.hp)
        assertEquals(100, snap.player.maxHp)
        assertEquals(ExperienceProgression.STARTING_LEVEL, snap.player.level)
        assertEquals(0, snap.player.experience)
        assertEquals(ExperienceProgression.xpRequiredForNextLevel(1), snap.player.experienceToNextLevel)
        assertEquals(ExperienceProgression.attackDamageForLevel(1), snap.player.attackDamage)
        assertEquals(snap.width * snap.height, snap.tiles.size)
        assertTrue(snap.mobs.isNotEmpty(), "expected starter mobs in session")
    }

    @Test
    fun `player spawns on a walkable floor tile`() {
        val engine = GameEngine()
        val snap = engine.createSession(seed = 123L)

        val gx = floor(snap.player.pose.x).toInt()
        val gy = floor(snap.player.pose.y).toInt()
        assertEquals(TileType.FLOOR, snap.tiles[gy * snap.width + gx])
    }

    @Test
    fun `same seed yields the same map across sessions`() {
        val a = GameEngine().createSession(seed = 999L)
        val b = GameEngine().createSession(seed = 999L)
        assertEquals(a.tiles, b.tiles)
        assertEquals(a.player.pose, b.player.pose)
    }

    @Test
    fun `generated labyrinth contains columns and lava decoration`() {
        val snap = GameEngine().createSession(seed = 5L)
        assertTrue(snap.tiles.any { it == TileType.COLUMN }, "expected columns in the labyrinth")
        assertTrue(snap.tiles.any { it == TileType.LAVA }, "expected lava in the labyrinth")
    }

    @Test
    fun `two-level session starts on the ground level with elevators`() {
        val snap = GameEngine().createSession(seed = 5L, twoLevel = true)
        assertEquals(0, snap.currentLevel)
        assertTrue(snap.tiles.any { it == TileType.ELEVATOR }, "expected elevators on the ground level")
    }

    @Test
    fun `single-level session has no elevators`() {
        val snap = GameEngine().createSession(seed = 5L)
        assertEquals(0, snap.currentLevel)
        assertTrue(snap.tiles.none { it == TileType.ELEVATOR })
    }
}
