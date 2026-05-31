package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.model.GridPos
import kotlin.random.Random

data class KeyPickup(
    val id: Int,
    val x: Float,
    val y: Float,
    var collected: Boolean = false,
) {
    fun toSnapshot() = KeySnapshot(id = id, x = x, y = y)
}

/** Расстановка ключей в обычных комнатах лабиринта. */
object KeySpawner {
    const val DEFAULT_KEY_COUNT = 3

    fun spawn(level: GeneratedLevel, seed: Long, count: Int = DEFAULT_KEY_COUNT): List<KeyPickup> {
        val safeCells = MapConnectivity.reachableSafeFloorCells(level.map, level.playerSpawn)
        val random = Random(seed xor KEY_SALT)
        val roomsWithSpots = level.rooms
            .filter { !it.isBoss }
            .mapNotNull { room ->
                keySpawnCellsInRoom(room, safeCells)
                    .takeIf { it.isNotEmpty() }
                    ?.let { room to it }
            }
            .shuffled(random)
            .take(count)

        return roomsWithSpots.mapIndexed { index, (_, spots) ->
            val cell = spots[random.nextInt(spots.size)]
            KeyPickup(id = index, x = cell.x + 0.5f, y = cell.y + 0.5f)
        }
    }

    fun bossRoomOf(level: GeneratedLevel): Room? = level.rooms.singleOrNull { it.isBoss }

    private fun keySpawnCellsInRoom(
        room: Room,
        safeCells: Set<GridPos>,
    ): List<GridPos> {
        val cells = mutableListOf<GridPos>()
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                val pos = GridPos(x, y)
                if (pos in safeCells) {
                    cells.add(pos)
                }
            }
        }
        return cells
    }

    private const val KEY_SALT = 0x4B_45_59_53L
}
