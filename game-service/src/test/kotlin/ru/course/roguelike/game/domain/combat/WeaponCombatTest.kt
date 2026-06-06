package ru.course.roguelike.game.domain.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.inventory.AddItemResult
import ru.course.roguelike.game.domain.inventory.InventorySystem
import ru.course.roguelike.game.domain.inventory.StarterLoadout
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.InventoryItemType
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class WeaponCombatTest {
    private fun session(): GameSession {
        val tiles = Array(25) { TileType.FLOOR }
        return GameSession(
            sessionId = "weapons",
            seed = 1L,
            map = TileMap(5, 5, tiles),
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
        ).also { StarterLoadout.apply(it) }
    }

    @Test
    fun `shotgun fires multiple pellets and costs two ammo`() {
        val session = session()
        val shotgun = session.inventory.add(InventoryItemType.SHOTGUN) as AddItemResult.Added
        session.hotbarSlots[1] = shotgun.item.id
        InventorySystem.handleHotbarInput(session, hotbarSelect = 1, hotbarAssign = null, reload = false)
        session.playerAmmo = 10

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(5, session.projectiles.size)
        assertEquals(8, session.playerAmmo)
    }

    @Test
    fun `pistol fires single projectile`() {
        val session = session()
        session.playerAmmo = 5

        CombatSystem.tick(session, deltaMs = 50, playerAttacking = true)

        assertEquals(1, session.projectiles.size)
        assertEquals(4, session.playerAmmo)
    }
}
