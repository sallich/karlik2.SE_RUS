package ru.course.roguelike.game.domain.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.ai.RusherBehavior
import ru.course.roguelike.game.domain.ai.ShooterBehavior
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class CombatSystemTest {
    private fun openArenaSession(playerPose: PlayerPose = PlayerPose(2.5f, 2.5f, yaw = 0f)): GameSession {
        val tiles = Array(25) { TileType.FLOOR }
        for (x in 0 until 5) {
            tiles[x] = TileType.WALL
            tiles[4 * 5 + x] = TileType.WALL
            tiles[x * 5] = TileType.WALL
            tiles[x * 5 + 4] = TileType.WALL
        }
        return GameSession(
            sessionId = "combat",
            seed = 1L,
            map = TileMap(5, 5, tiles),
            playerPose = playerPose,
        )
    }

    @Test
    fun `player attack spawns a projectile instead of instant hit`() {
        val session = openArenaSession()
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f)
        session.mobs.add(mob)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(1, session.projectiles.size)
        assertTrue(session.projectiles.first().fromPlayer)
        assertEquals(CombatConstants.MELEE_MOB_HP, mob.hp)
    }

    @Test
    fun `player projectile damages mob on impact`() {
        val session = openArenaSession()
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f)
        session.mobs.add(mob)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)
        repeat(30) {
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }

        assertTrue(mob.hp < CombatConstants.MELEE_MOB_HP)
    }

    @Test
    fun `player projectile kills mob at zero hp`() {
        val session = openArenaSession()
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f)
        mob.hp = 10
        session.mobs.add(mob)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)
        repeat(40) {
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }

        assertEquals(0, mob.hp)
        assertTrue(session.mobs.isEmpty())
    }

    @Test
    fun `melee mob damages player when in range`() {
        val session = openArenaSession(PlayerPose(2.5f, 2.5f, yaw = 0f))
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.1f, 2.5f)
        mob.attackCooldownMs = 0
        session.mobs.add(mob)

        val events = CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)

        assertEquals(100 - CombatConstants.MELEE_MOB_DAMAGE, session.playerHp)
        assertTrue(events.any { it is GameEvent.PlayerDamaged })
    }

    @Test
    fun `ranged mob fires projectile that damages player`() {
        val session = openArenaSession(PlayerPose(2.5f, 2.5f, yaw = 0f))
        val mob = MobSpawner.createMob(session, MobKind.RANGED, 2.5f, 4f)
        mob.attackCooldownMs = 0
        session.mobs.add(mob)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        assertEquals(1, session.projectiles.size)
        assertTrue(!session.projectiles.first().fromPlayer)

        repeat(40) {
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }

        assertTrue(session.playerHp < 100)
        assertTrue(session.projectiles.isEmpty())
    }

    @Test
    fun `melee mob moves toward player over time`() {
        val session = openArenaSession()
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f)
        session.mobs.add(mob)
        val startX = mob.x

        repeat(20) {
            CombatSystem.tick(session, deltaMs = 100, playerAttacking = false)
        }

        assertTrue(mob.x < startX)
    }

    @Test
    fun `rusher and shooter behaviors are wired to mob kinds`() {
        val melee = MobSpawner.createMob(openArenaSession(), MobKind.MELEE, 3f, 3f)
        val ranged = MobSpawner.createMob(openArenaSession(), MobKind.RANGED, 3f, 3f)
        assertTrue(melee.behavior is RusherBehavior)
        assertTrue(ranged.behavior is ShooterBehavior)
    }
}
