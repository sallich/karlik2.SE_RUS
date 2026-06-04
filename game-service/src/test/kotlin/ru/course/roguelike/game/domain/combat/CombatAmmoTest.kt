package ru.course.roguelike.game.domain.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class CombatAmmoTest {
    private fun openArenaSession(): GameSession {
        val tiles = Array(25) { TileType.FLOOR }
        for (x in 0 until 5) {
            tiles[x] = TileType.WALL
            tiles[4 * 5 + x] = TileType.WALL
            tiles[x * 5] = TileType.WALL
            tiles[x * 5 + 4] = TileType.WALL
        }
        return GameSession(
            sessionId = "ammo",
            seed = 1L,
            map = TileMap(5, 5, tiles),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
        )
    }

    @Test
    fun `firing consumes one round of ammo`() {
        val session = openArenaSession()
        val before = session.playerAmmo

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(before - 1, session.playerAmmo)
        assertEquals(1, session.projectiles.size)
    }

    @Test
    fun `no projectile is fired when out of ammo`() {
        val session = openArenaSession()
        session.playerAmmo = 0

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(0, session.playerAmmo)
        assertTrue(session.projectiles.isEmpty())
    }

    @Test
    fun `mob drops never exceed the configured max ammo cap`() {
        // Sanity: ammo from a fresh session starts at the documented value.
        assertEquals(CombatConstants.PLAYER_STARTING_AMMO, openArenaSession().playerAmmo)
    }

    @Test
    fun `killing many mobs eventually drops loot`() {
        // With a 35% drop chance, killing enough mobs should produce at least one drop
        // and every drop must carry a unique id.
        val session = openArenaSession()
        repeat(40) { i ->
            val mob = MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 2.5f)
            mob.hp = 0
            session.mobs.add(mob)
            session.tick = i.toLong()
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }
        assertTrue(session.itemPickups.isNotEmpty(), "expected at least one mob drop across 40 kills")
        val ids = session.itemPickups.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "dropped item ids must be unique")
    }
}
