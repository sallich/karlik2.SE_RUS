package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.StarterLoadout
import ru.course.roguelike.game.domain.progression.ProgressionSystem
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.InventoryItemType
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
        ).also { StarterLoadout.apply(it) }
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
    fun `health item auto-heals when picked up injured`() {
        val session = session()
        session.playerHp = 40
        itemAtPlayer(session, ItemKind.HEALTH)

        val events = ItemPickupSystem.apply(session, session.playerPose)

        assertTrue(events.any { it is GameEvent.PlayerHealed })
        assertTrue(session.playerHp > 40)
    }

    @Test
    fun `health item stays in inventory at full hp`() {
        val session = session()
        itemAtPlayer(session, ItemKind.HEALTH)

        ItemPickupSystem.apply(session, session.playerPose)

        assertTrue(session.inventory.items.any { it.type == InventoryItemType.HEALTH_KIT })
    }

    @Test
    fun `pistol and shotgun ammo stack separately`() {
        val session = session()
        itemAtPlayer(session, ItemKind.AMMO_SHOTGUN)
        ItemPickupSystem.apply(session, session.playerPose)

        assertTrue(session.inventory.items.any { it.type == InventoryItemType.SHOTGUN_AMMO })
        assertTrue(session.inventory.items.any { it.type == InventoryItemType.PISTOL_AMMO })
    }

    @Test
    fun `weapon requires interact to pick up`() {
        val session = session()
        itemAtPlayer(session, ItemKind.WEAPON_PISTOL)

        assertTrue(ItemPickupSystem.apply(session, session.playerPose, interact = false).isEmpty())
        ItemPickupSystem.apply(session, session.playerPose, interact = true)
        assertTrue(session.itemPickups.single().collected)
    }

    @Test
    fun `weapon bonus survives level ups`() {
        val session = session()
        itemAtPlayer(session, ItemKind.WEAPON_PISTOL)
        ItemPickupSystem.apply(session, session.playerPose, interact = true)
        val damageAfterPickup = session.playerAttackDamage
        session.playerExperience = 0
        ProgressionSystem.awardItemXp(session, 1_000)
        assertTrue(session.playerAttackDamage >= damageAfterPickup)
    }
}
