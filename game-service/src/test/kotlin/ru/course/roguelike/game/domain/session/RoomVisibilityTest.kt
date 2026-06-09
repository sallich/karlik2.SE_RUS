package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.combat.MobSpawner
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class RoomVisibilityTest {
    private val roomA = Room(1, 1, 4, 4)
    private val roomB = Room(8, 1, 4, 4)

    @Test
    fun `weapon and key in an uncleared room are hidden, ammo stays visible`() {
        val session = session()
        session.mobs.add(MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 3.5f, roomA))

        val snapshot = session.toSnapshot()

        assertFalse(snapshot.items.any { it.kind == ItemKind.WEAPON_PISTOL }, "weapon hidden until cleared")
        assertTrue(snapshot.items.any { it.kind == ItemKind.AMMO_PISTOL }, "ammo is auto-pickup, stays visible")
        assertTrue(snapshot.keyPickups.isEmpty(), "key hidden until cleared")
        assertTrue(snapshot.keysRequired >= 1, "hidden key still counts toward the requirement")
        assertTrue(snapshot.doorMarkers.isNotEmpty(), "door marker advertises the prize")
        assertNull(snapshot.doorMarkers.first().kind, "key takes priority over the weapon (null kind = key)")
    }

    @Test
    fun `clearing the room reveals the prizes and removes the markers`() {
        val session = session()
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 3.5f, roomA)
        session.mobs.add(mob)

        mob.hp = 0
        RoomEngagementSystem.tick(session)
        val snapshot = session.toSnapshot()

        assertTrue(snapshot.items.any { it.kind == ItemKind.WEAPON_PISTOL }, "weapon revealed after clear")
        assertTrue(snapshot.keyPickups.isNotEmpty(), "key revealed after clear")
        assertTrue(snapshot.doorMarkers.isEmpty(), "markers gone after clear")
    }

    private fun session(): GameSession {
        val map = corridorMap()
        return GameSession(
            sessionId = "visibility",
            seed = 1L,
            map = map,
            playerPose = PlayerPose(2.5f, 2.5f, yaw = 0f),
            rooms = listOf(roomA, roomB),
            roomEngagements = listOf(roomA, roomB).mapIndexed { index, room ->
                RoomEngagementState(roomIndex = index, doorways = RoomDoorways.of(map, room))
            }.toMutableList(),
            keyPickups = mutableListOf(KeyPickup(id = 0, x = 2.5f, y = 2.5f)),
            itemPickups = mutableListOf(
                ItemPickup(id = 0, kind = ItemKind.WEAPON_PISTOL, x = 3.5f, y = 2.5f),
                ItemPickup(id = 1, kind = ItemKind.AMMO_PISTOL, x = 1.5f, y = 2.5f),
            ),
        )
    }

    private fun corridorMap(): TileMap {
        val width = roomB.x + roomB.width + 1
        val height = maxOf(roomA.y + roomA.height, roomB.y + roomB.height) + 1
        val tiles = Array(width * height) { TileType.WALL }
        fun floor(x: Int, y: Int) {
            tiles[y * width + x] = TileType.FLOOR
        }
        for (y in roomA.y until roomA.y + roomA.height) for (x in roomA.x until roomA.x + roomA.width) floor(x, y)
        for (y in roomB.y until roomB.y + roomB.height) for (x in roomB.x until roomB.x + roomB.width) floor(x, y)
        for (x in roomA.x + roomA.width until roomB.x) floor(x, roomA.center.y)
        return TileMap(width, height, tiles)
    }
}
