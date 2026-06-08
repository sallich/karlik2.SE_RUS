package ru.course.roguelike.game.domain.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.ai.RusherBehavior
import ru.course.roguelike.game.domain.ai.ShooterBehavior
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class CombatSystemTest {
    private val arenaRoom = Room(1, 1, 3, 3)

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
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, arenaRoom)
        session.mobs.add(mob)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(1, session.projectiles.size)
        assertTrue(session.projectiles.first().fromPlayer)
        assertEquals(CombatConstants.MELEE_MOB_HP, mob.hp)
    }

    @Test
    fun `player projectile damages mob on impact`() {
        val session = openArenaSession()
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, arenaRoom)
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
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, arenaRoom)
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
    fun `killing a mob awards experience`() {
        val session = openArenaSession()
        session.locationCompletionAwarded = true
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, arenaRoom)
        mob.hp = 10
        session.mobs.add(mob)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)
        repeat(40) {
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }

        assertEquals(ExperienceProgression.MELEE_MOB_XP, session.playerExperience)
    }

    @Test
    fun `clearing all mobs awards location completion xp`() {
        val session = openArenaSession()
        val melee = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, arenaRoom)
        val ranged = MobSpawner.createMob(session, MobKind.RANGED, 2.5f, 3.5f, arenaRoom)
        melee.hp = 10
        ranged.hp = 10
        session.mobs.add(melee)
        session.mobs.add(ranged)

        fun killAllMobs() {
            session.mobs.forEach { mob ->
                mob.hp = 0
            }
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }
        killAllMobs()

        val expectedXp = ExperienceProgression.MELEE_MOB_XP +
            ExperienceProgression.RANGED_MOB_XP +
            ExperienceProgression.LOCATION_COMPLETION_XP
        assertEquals(expectedXp, session.playerExperience)
        assertTrue(session.locationCompletionAwarded)
    }

    @Test
    fun `level up increases player attack damage used in combat`() {
        val session = openArenaSession()
        session.playerLevel = 2
        session.playerAttackDamage = ExperienceProgression.attackDamageForLevel(2)

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(ExperienceProgression.attackDamageForLevel(2), session.projectiles.first().damage)
    }

    @Test
    fun `melee mob damages player when in range`() {
        val session = openArenaSession(PlayerPose(2.5f, 2.5f, yaw = 0f))
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.1f, 2.5f, arenaRoom)
        mob.attackCooldownMs = 0
        session.mobs.add(mob)

        val events = CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)

        assertEquals(100 - CombatConstants.MELEE_MOB_DAMAGE, session.playerHp)
        assertTrue(events.any { it is GameEvent.PlayerDamaged })
    }

    @Test
    fun `ranged mob fires projectile that damages player`() {
        val session = openArenaSession(PlayerPose(2.5f, 2.5f, yaw = 0f))
        val mob = MobSpawner.createMob(session, MobKind.RANGED, 2.5f, 4f, arenaRoom)
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
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 3.5f, 2.5f, arenaRoom)
        session.mobs.add(mob)
        val startX = mob.x

        repeat(20) {
            CombatSystem.tick(session, deltaMs = 100, playerAttacking = false)
        }

        assertTrue(mob.x < startX)
    }

    @Test
    fun `player projectile uses pitch for vertical velocity`() {
        val session = openArenaSession(PlayerPose(2.5f, 2.5f, yaw = 0f, pitch = 0.4f))
        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)
        val projectile = session.projectiles.first()
        assertTrue(projectile.velocityZ > 0f)
        assertTrue(projectile.z > 0f)
    }

    @Test
    fun `mob does not chase player outside its room`() {
        val room = Room(2, 2, 4, 4)
        val map = TileMap(10, 10, Array(100) { TileType.FLOOR })
        val session = GameSession(
            sessionId = "corridor",
            seed = 1L,
            map = map,
            playerPose = PlayerPose(1.5f, 4.5f, yaw = 0f),
        )
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 4.5f, 4.5f, room)
        session.mobs.add(mob)
        val startX = mob.x

        repeat(20) {
            CombatSystem.tick(session, deltaMs = 100, playerAttacking = false)
        }

        assertEquals(startX, mob.x, 0.02f)
    }

    @Test
    fun `mob chases player inside its room`() {
        val room = Room(2, 2, 4, 4)
        val map = TileMap(10, 10, Array(100) { TileType.FLOOR })
        val session = GameSession(
            sessionId = "room-chase",
            seed = 1L,
            map = map,
            playerPose = PlayerPose(3.5f, 4.5f, yaw = 0f),
        )
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 5.5f, 4.5f, room)
        session.mobs.add(mob)
        val startX = mob.x

        repeat(20) {
            CombatSystem.tick(session, deltaMs = 100, playerAttacking = false)
        }

        assertTrue(mob.x < startX)
    }

    @Test
    fun `melee mob cannot walk through a column`() {
        val tiles = Array(25) { TileType.FLOOR }
        tiles[2 * 5 + 2] = TileType.COLUMN
        val map = TileMap(5, 5, tiles)
        val room = Room(0, 0, 5, 5)
        val session = GameSession(
            sessionId = "column-block",
            seed = 1L,
            map = map,
            playerPose = PlayerPose(0.5f, 2.5f, yaw = 0f),
        )
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 1.5f, 2.5f, room)
        session.mobs.add(mob)

        repeat(40) {
            CombatSystem.tick(session, deltaMs = 50, playerAttacking = false)
        }

        assertFalse(
            EntityCollision.overlapsMovement(
                map,
                EntityCollision.Circle(mob.x, mob.y, CombatConstants.MOB_RADIUS),
                localHeight = 0f,
            ),
            "mob at (${mob.x}, ${mob.y}) should not overlap column",
        )
    }

    @Test
    fun `rusher and shooter behaviors are wired to mob kinds`() {
        val melee = MobSpawner.createMob(openArenaSession(), MobKind.MELEE, 3f, 3f, arenaRoom)
        val ranged = MobSpawner.createMob(openArenaSession(), MobKind.RANGED, 3f, 3f, arenaRoom)
        assertTrue(melee.behavior is RusherBehavior)
        assertTrue(ranged.behavior is ShooterBehavior)
    }
}
