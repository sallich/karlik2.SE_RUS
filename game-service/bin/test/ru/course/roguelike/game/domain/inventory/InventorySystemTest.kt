package ru.course.roguelike.game.domain.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.combat.CombatSystem
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.InventoryConstants
import ru.course.roguelike.shared.model.InventoryItemType
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class InventorySystemTest {
    private fun session(): GameSession {
        val tiles = Array(25) { TileType.FLOOR }
        return GameSession(
            sessionId = "inv",
            seed = 1L,
            map = TileMap(5, 5, tiles),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
        ).also { StarterLoadout.apply(it) }
    }

    @Test
    fun `starter has two hotbar slots with pistol in first`() {
        val session = session()
        assertEquals(2, session.hotbarSlots.size)
        assertTrue(session.hotbarSlots[0] != null)
        assertEquals(InventoryConstants.STARTING_LOADED_AMMO, session.playerAmmo)
    }

    @Test
    fun `F reload fills magazine up to weapon capacity`() {
        val session = session()
        session.playerAmmo = 0

        InventorySystem.handleHotbarInput(session, null, null, reload = true)

        assertEquals(12, session.playerAmmo)
        assertEquals(8, session.inventory.totalAmmo(InventoryItemType.PISTOL_AMMO))
    }

    @Test
    fun `manual reload tops up partial magazine`() {
        val session = session()
        session.playerAmmo = 5

        InventorySystem.handleHotbarInput(session, null, null, reload = true)

        assertEquals(12, session.playerAmmo)
    }

    @Test
    fun `shotgun reload respects smaller magazine`() {
        val session = session()
        val shotgun = session.inventory.add(InventoryItemType.SHOTGUN) as AddItemResult.Added
        session.inventory.add(InventoryItemType.SHOTGUN_AMMO, InventoryConstants.AMMO_STACK_SIZE)
        session.hotbarSlots[1] = shotgun.item.id
        InventorySystem.handleHotbarInput(session, hotbarSelect = 1, null, reload = false)
        session.playerAmmo = 0

        InventorySystem.handleHotbarInput(session, null, null, reload = true)

        assertEquals(8, session.playerAmmo)
    }

    @Test
    fun `F reload pulls matching ammo from inventory automatically`() {
        val session = session()
        session.playerAmmo = 0

        val events = InventorySystem.handleHotbarInput(session, null, null, reload = true)

        assertTrue(events.any { it is GameEvent.AmmoChanged })
        assertTrue(session.playerAmmo > 0)
    }

    @Test
    fun `hotbar assign cycles weapons into slot`() {
        val session = session()
        val shotgun = session.inventory.add(InventoryItemType.SHOTGUN) as AddItemResult.Added

        InventorySystem.handleHotbarInput(session, null, hotbarAssign = 1, reload = false)

        assertEquals(shotgun.item.id, session.hotbarSlots[1])
    }

    @Test
    fun `pressing hotbar slot equips weapon`() {
        val session = session()
        val shotgun = session.inventory.add(InventoryItemType.SHOTGUN) as AddItemResult.Added
        session.hotbarSlots[1] = shotgun.item.id

        InventorySystem.handleHotbarInput(session, hotbarSelect = 1, null, reload = false)

        assertEquals(shotgun.item.id, session.equippedWeaponItemId)
    }

    @Test
    fun `reload ignores wrong ammo type`() {
        val session = session()
        session.inventory.items.removeAll { it.type == InventoryItemType.PISTOL_AMMO }
        session.inventory.add(InventoryItemType.SHOTGUN_AMMO, InventoryConstants.AMMO_STACK_SIZE)
        session.playerAmmo = 0

        val events = InventoryWeapons.reloadEquippedWeapon(session)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `weapon switch preserves loaded ammo per weapon`() {
        val session = session()
        val pistolId = session.equippedWeaponItemId!!
        session.playerAmmo = 7
        session.weaponLoadedAmmo[pistolId] = 7
        val shotgun = session.inventory.add(InventoryItemType.SHOTGUN) as AddItemResult.Added
        session.hotbarSlots[1] = shotgun.item.id
        session.weaponLoadedAmmo[shotgun.item.id] = 4

        InventorySystem.handleHotbarInput(session, hotbarSelect = 1, null, reload = false)

        assertEquals(4, session.playerAmmo)
        assertEquals(7, session.weaponLoadedAmmo[pistolId])
    }

    @Test
    fun `auto reload after firing last round fills magazine`() {
        val session = session()
        session.playerAmmo = 1

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(12, session.playerAmmo)
    }
}
