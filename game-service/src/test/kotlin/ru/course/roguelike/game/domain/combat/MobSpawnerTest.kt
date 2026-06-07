package ru.course.roguelike.game.domain.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.infrastructure.level.LabyrinthLevelGenerator
import ru.course.roguelike.game.infrastructure.level.TwoLevelLabyrinthGenerator
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

class MobSpawnerTest {
    @Test
    fun `each non-start room gets melee and ranged mobs`() {
        for (seed in 1L..30L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val session = emptySession(level)
            MobSpawner.spawnForLevel(session, level, seed)

            val startRoom = level.rooms.first { it.contains(level.playerSpawn) }
            for (room in level.rooms.filter { it != startRoom }) {
                val mobsInRoom = session.mobs.filter { mob ->
                    room.contains(GridPos(floor(mob.x).toInt(), floor(mob.y).toInt()))
                }
                val kinds = mobsInRoom.map { it.kind }.toSet()
                assertTrue(
                    MobKind.MELEE in kinds && MobKind.RANGED in kinds,
                    "seed=$seed room at ${room.center} missing mob kinds: $kinds",
                )
                assertTrue(
                    mobsInRoom.count { it.kind == MobKind.MELEE } >= MobSpawner.MIN_MOBS_PER_KIND,
                    "seed=$seed room at ${room.center} has too few melee mobs",
                )
                assertTrue(
                    mobsInRoom.count { it.kind == MobKind.RANGED } >= MobSpawner.MIN_MOBS_PER_KIND,
                    "seed=$seed room at ${room.center} has too few ranged mobs",
                )
            }
        }
    }

    @Test
    fun `mobs spawn only on reachable safe floor tiles`() {
        for (seed in 1L..30L) {
            val level = LabyrinthLevelGenerator.generate(seed)
            val safeCells = MapConnectivity.reachableSafeFloorCells(level.map, level.playerSpawn)
            val session = emptySession(level)
            MobSpawner.spawnForLevel(session, level, seed)

            for (mob in session.mobs.filter { it.kind != MobKind.LLM_GUARD }) {
                val cell = GridPos(floor(mob.x).toInt(), floor(mob.y).toInt())
                assertTrue(cell in safeCells, "seed=$seed mob at $cell is not on safe floor")
                assertEquals(TileType.FLOOR, level.map.get(cell), "seed=$seed mob tile at $cell is not FLOOR")
            }
        }
    }

    @Test
    fun `larger rooms receive more mobs than smaller ones on average`() {
        val level = LabyrinthLevelGenerator.generate(42L)
        val safeCells = MapConnectivity.reachableSafeFloorCells(level.map, level.playerSpawn)
        val startRoom = level.rooms.first { it.contains(level.playerSpawn) }
        val plans = level.rooms
            .filter { it != startRoom }
            .associateWith { room ->
                RoomMobPlanner.mobPlanForRoom(RoomMobPlanner.roomLayoutMetrics(room, level.map, safeCells))
            }
        val smallestEntry = plans.minBy { it.key.area }
        val largestEntry = plans.maxBy { it.key.area }
        assertTrue(
            largestEntry.value.total > smallestEntry.value.total,
            "expected larger room ${largestEntry.key.area} to have more mobs than ${smallestEntry.key.area}",
        )
    }

    @Test
    fun `columns and elevators increase ranged mob share`() {
        val room = Room(x = 1, y = 1, width = 8, height = 8)
        val openTiles = flatMap(room) { _, _ -> TileType.FLOOR }
        val openMap = tileMap(openTiles, 10, 10)
        val obstructedTiles = flatMap(room) { x, y ->
            when {
                x == 3 && y in 2..6 -> TileType.COLUMN
                x == 5 && y == 4 -> TileType.ELEVATOR
                else -> TileType.FLOOR
            }
        }
        val obstructedMap = tileMap(obstructedTiles, 10, 10)
        val safeCells = walkableCells(openMap)

        val openPlan = RoomMobPlanner.mobPlanForRoom(
            RoomMobPlanner.roomLayoutMetrics(room, openMap, safeCells),
        )
        val obstructedPlan = RoomMobPlanner.mobPlanForRoom(
            RoomMobPlanner.roomLayoutMetrics(room, obstructedMap, safeCells),
        )

        val openRangedShare = openPlan.rangedCount.toFloat() / openPlan.total
        val obstructedRangedShare = obstructedPlan.rangedCount.toFloat() / obstructedPlan.total
        assertTrue(
            obstructedRangedShare > openRangedShare,
            "expected more ranged mobs with columns/elevator: open=$openRangedShare obstructed=$obstructedRangedShare",
        )
    }

    @Test
    fun `two-level ground map spawns mobs with elevator-aware plans`() {
        val dungeon = TwoLevelLabyrinthGenerator.generate(7L)
        val ground = dungeon.levels[0]
        val session = emptySession(ground)
        MobSpawner.spawnForLevel(session, ground, 7L)

        assertTrue(session.mobs.any { it.kind == MobKind.MELEE })
        assertTrue(session.mobs.any { it.kind == MobKind.RANGED })
        assertTrue(ground.map.toFlatList().any { it == TileType.ELEVATOR })
    }

    private fun emptySession(level: GeneratedLevel): GameSession =
        GameSession(
            sessionId = "test",
            seed = 0L,
            map = level.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
        )

    private fun tileMap(tiles: List<TileType>, width: Int, height: Int): TileMap =
        TileMap.fromFlat(width, height, tiles)

    private fun flatMap(room: Room, tileAt: (Int, Int) -> TileType): List<TileType> {
        val width = room.x + room.width + 1
        val height = room.y + room.height + 1
        return Array(width * height) { TileType.WALL }
            .also { arr ->
                for (y in room.y until room.y + room.height) {
                    for (x in room.x until room.x + room.width) {
                        arr[y * width + x] = tileAt(x, y)
                    }
                }
            }
            .toList()
    }

    private fun walkableCells(map: TileMap): Set<GridPos> {
        val cells = mutableSetOf<GridPos>()
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (map.get(GridPos(x, y))?.walkable == true) cells.add(GridPos(x, y))
            }
        }
        return cells
    }
}
