package ru.course.roguelike.game.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.combat.CombatSystem
import ru.course.roguelike.game.domain.combat.MobSpawner
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType

class RoomEngagementSystemTest {
    @Test
    fun `entering via door seals exits and starts timer`() {
        val roomA = Room(1, 1, 4, 4)
        val roomB = Room(8, 1, 4, 4)
        val map = corridorMap(roomA, roomB)
        val doorway = RoomDoorways.of(map, roomA).single()
        val session = sessionWithDoorways(
            map,
            listOf(roomA, roomB),
            PlayerPose(doorway.x + 1.0f, doorway.y + 0.5f, yaw = 0f),
        )
        session.mobs.add(MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 3.5f, roomA))
        session.map.setTile(doorway, TileType.ROOM_DOOR)

        RoomDoorInteract.tryEnter(session, session.playerPose)

        assertTrue(session.roomEngagements[0].entered)
        assertTrue(session.roomEngagements[0].doorsLocked)
        assertNotNull(session.roomEngagements[0].timerStartedAtMs)
        assertEquals(TileType.ROOM_SEAL, session.map.get(doorway))
        assertNotNull(RoomEngagementSystem.timerSnapshot(session))
    }

    @Test
    fun `reinforcements trigger after timer expires without clear`() {
        val roomA = Room(1, 1, 4, 4)
        val roomB = Room(8, 1, 4, 4)
        val map = corridorMap(roomA, roomB)
        val session = sessionWithDoorways(map, listOf(roomA, roomB), PlayerPose(2.5f, 2.5f, yaw = 0f))
        session.mobs.add(MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 3.5f, roomA))
        val neighborMob = MobSpawner.createMob(session, MobKind.MELEE, 9.5f, 3.5f, roomB)
        session.mobs.add(neighborMob)

        session.roomEngagements[0].entered = true
        session.roomEngagements[0].doorsLocked = true
        session.roomEngagements[0].timerStartedAtMs = session.serverTimeMs - RoomEngagementConstants.CLEAR_TIMER_MS - 1

        RoomEngagementSystem.tick(session)

        assertTrue(session.roomEngagements[0].reinforcementsTriggered)
        assertEquals(roomA, neighborMob.reinforceTarget)
    }

    @Test
    fun `clearing a room removes seals and reveals prize`() {
        val roomA = Room(1, 1, 4, 4)
        val roomB = Room(8, 1, 4, 4)
        val map = corridorMap(roomA, roomB)
        val session = sessionWithDoorways(map, listOf(roomA, roomB), PlayerPose(2.5f, 2.5f, yaw = 0f))
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 2.5f, 3.5f, roomA)
        session.mobs.add(mob)
        val doorway = session.roomEngagements[0].doorways.single()
        session.roomEngagements[0].entered = true
        session.roomEngagements[0].doorsLocked = true
        session.roomEngagements[0].timerStartedAtMs = session.serverTimeMs
        session.map.setTile(doorway, TileType.ROOM_SEAL)

        mob.hp = 0
        RoomEngagementSystem.tick(session)

        assertTrue(session.roomEngagements[0].cleared)
        assertFalse(session.roomEngagements[0].doorsLocked)
        assertEquals(TileType.FLOOR, session.map.get(doorway))
        assertNull(RoomEngagementSystem.timerSnapshot(session))
    }

    @Test
    fun `reinforcing mob moves toward target room`() {
        val roomA = Room(1, 1, 4, 4)
        val roomB = Room(8, 1, 4, 4)
        val map = corridorMap(roomA, roomB)
        val session = sessionWithDoorways(map, listOf(roomA, roomB), PlayerPose(2.5f, 2.5f, yaw = 0f))
        val mob = MobSpawner.createMob(session, MobKind.MELEE, 9.5f, 3.5f, roomB)
        mob.reinforceTarget = roomA
        session.mobs.add(mob)
        val startX = mob.x

        repeat(30) {
            CombatSystem.tick(session, deltaMs = 100, playerAttacking = false)
        }

        assertTrue(mob.x < startX, "mob should march from room B toward room A")
    }

    private fun sessionWithRooms(map: TileMap, rooms: List<Room>, pose: PlayerPose): GameSession =
        GameSession(
            sessionId = "engagement",
            seed = 1L,
            map = map,
            playerPose = pose,
            rooms = rooms,
            roomEngagements = rooms.mapIndexed { index, _ -> RoomEngagementState(roomIndex = index) }.toMutableList(),
        )

    private fun sessionWithDoorways(map: TileMap, rooms: List<Room>, pose: PlayerPose): GameSession =
        GameSession(
            sessionId = "engagement-doors",
            seed = 1L,
            map = map,
            playerPose = pose,
            rooms = rooms,
            roomEngagements = rooms.mapIndexed { index, room ->
                RoomEngagementState(roomIndex = index, doorways = RoomDoorways.of(map, room))
            }.toMutableList(),
        )

    private fun corridorMap(roomA: Room, roomB: Room): TileMap {
        val width = roomB.x + roomB.width + 1
        val height = maxOf(roomA.y + roomA.height, roomB.y + roomB.height) + 1
        val tiles = Array(width * height) { TileType.WALL }
        fun setFloor(x: Int, y: Int) {
            tiles[y * width + x] = TileType.FLOOR
        }
        for (y in roomA.y until roomA.y + roomA.height) {
            for (x in roomA.x until roomA.x + roomA.width) setFloor(x, y)
        }
        for (y in roomB.y until roomB.y + roomB.height) {
            for (x in roomB.x until roomB.x + roomB.width) setFloor(x, y)
        }
        val corridorY = roomA.center.y
        for (x in roomA.x + roomA.width until roomB.x) setFloor(x, corridorY)
        return TileMap(width, height, tiles)
    }
}
