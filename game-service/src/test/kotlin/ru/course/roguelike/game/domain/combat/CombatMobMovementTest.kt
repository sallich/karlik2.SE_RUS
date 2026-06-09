package ru.course.roguelike.game.domain.combat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.ai.mobBehaviorFor
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.TileType

class CombatMobMovementTest {
    /** Корридор-строка с печатью посередине (x=2). */
    private fun sealedCorridor(): TileMap {
        val width = 5
        val height = 3
        val tiles = Array(width * height) { TileType.WALL }
        for (x in 0 until width) tiles[1 * width + x] = TileType.FLOOR
        tiles[1 * width + 2] = TileType.ROOM_SEAL
        return TileMap(width, height, tiles)
    }

    private fun meleeMob(aggroRoom: Room) =
        MobEntity.MeleeMob(id = 1L, x = 0.5f, y = 1.5f, behavior = mobBehaviorFor(MobKind.MELEE), aggroRoom = aggroRoom)

    @Test
    fun `native room mob cannot cross a room seal`() {
        val map = sealedCorridor()
        val room = Room(0, 0, 5, 3)
        val mob = meleeMob(room)

        repeat(60) { CombatMobMovement.moveToward(map, mob, targetX = 4.5f, targetY = 1.5f, speed = 2f, deltaSec = 0.1f) }

        assertTrue(mob.x < 2f, "mob without a reinforce target must stay behind the seal, was ${mob.x}")
    }

    @Test
    fun `reinforcing mob passes through a room seal`() {
        val map = sealedCorridor()
        val room = Room(0, 0, 5, 3)
        val mob = meleeMob(room)
        mob.reinforceTarget = room

        repeat(60) { CombatMobMovement.moveToward(map, mob, targetX = 4.5f, targetY = 1.5f, speed = 2f, deltaSec = 0.1f) }

        assertTrue(mob.x > 3f, "reinforcing mob should march past the seal, was ${mob.x}")
    }
}
