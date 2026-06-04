package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class ItemPickupSystemTest {
    private fun session(pose: PlayerPose = PlayerPose(2.5f, 2.5f, yaw = 0f)): GameSession {
        val tiles = Array(25) { TileType.FLOOR }
        return GameSession(
            sessionId = "items",
            seed = 1L,
            map = TileMap(5, 5, tiles),
            playerPose = pose,
        )
    }

    private fun itemAtPlayer(session: GameSession, kind: ItemKind) {
        session.itemPickups.add(
            ItemPickup(
                id = session.allocateItemId(),
                kind = kind,
                x = session.playerPose.x,
                y = session.playerPose.y,
            ),
        )
    }

    @Test
    fun `health item heals and is capped at max hp`() {
        val session = session()
        session.playerHp = 50
        itemAtPlayer(session, ItemKind.HEALTH)

        val events = ItemPickupSystem.apply(session, session.playerPose)

        assertEquals(50 + ItemPickupSystem.HEALTH_RESTORE, session.playerHp)
        assertTrue(events.any { it is GameEvent.ItemCollected })
        assertTrue(events.any { it is GameEvent.PlayerHealed })
        assertTrue(session.itemPickups.single().collected)
    }

    @Test
    fun `health item never overheals`() {
        val session = session()
        session.playerHp = session.playerMaxHp
        itemAtPlayer(session, ItemKind.HEALTH)

        ItemPickupSystem.apply(session, session.playerPose)

        assertEquals(session.playerMaxHp, session.playerHp)
    }

    @Test
    fun `experience item awards xp`() {
        val session = session()
        session.locationCompletionAwarded = true
        itemAtPlayer(session, ItemKind.EXPERIENCE)

        ItemPickupSystem.apply(session, session.playerPose)

        assertEquals(ItemPickupSystem.EXPERIENCE_REWARD, session.playerExperience)
    }

    @Test
    fun `weapon item permanently raises attack damage and survives level ups`() {
        val session = session()
        val baseDamage = session.playerAttackDamage
        itemAtPlayer(session, ItemKind.WEAPON)

        ItemPickupSystem.apply(session, session.playerPose)
        assertEquals(baseDamage + ItemPickupSystem.WEAPON_DAMAGE_BONUS, session.playerAttackDamage)

        // After leveling up the weapon bonus must still be applied on top of the level damage.
        session.playerExperience = 0
        ru.course.roguelike.game.domain.progression.ProgressionSystem.awardItemXp(session, 1_000)
        assertEquals(
            ExperienceProgression.attackDamageForLevel(session.playerLevel) + ItemPickupSystem.WEAPON_DAMAGE_BONUS,
            session.playerAttackDamage,
        )
    }

    @Test
    fun `ammo item refills and is capped at max`() {
        val session = session()
        session.playerAmmo = CombatConstants.PLAYER_MAX_AMMO - 5
        itemAtPlayer(session, ItemKind.AMMO)

        ItemPickupSystem.apply(session, session.playerPose)

        assertEquals(CombatConstants.PLAYER_MAX_AMMO, session.playerAmmo)
    }

    @Test
    fun `item out of reach is not collected`() {
        val session = session()
        session.itemPickups.add(ItemPickup(id = 0, kind = ItemKind.HEALTH, x = 4.5f, y = 4.5f))
        session.playerHp = 50

        val events = ItemPickupSystem.apply(session, session.playerPose)

        assertTrue(events.isEmpty())
        assertEquals(50, session.playerHp)
        assertFalse(session.itemPickups.single().collected)
    }

    @Test
    fun `dead player collects nothing`() {
        val session = session()
        session.playerHp = 0
        itemAtPlayer(session, ItemKind.HEALTH)

        val events = ItemPickupSystem.apply(session, session.playerPose)

        assertTrue(events.isEmpty())
        assertFalse(session.itemPickups.single().collected)
    }
}
