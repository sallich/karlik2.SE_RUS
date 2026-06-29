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
        val candidates = level.rooms
            .filter { !it.isBoss }
            .mapNotNull { room ->
                keySpawnCellsInRoom(room, safeCells)
                    .takeIf { it.isNotEmpty() }
                    ?.let { room to it }
            }
        val roomsWithSpots = spreadAcrossLocation(candidates, level.playerSpawn, count, random)

        return roomsWithSpots.mapIndexed { index, (_, spots) ->
            val cell = spots[random.nextInt(spots.size)]
            KeyPickup(id = index, x = cell.x + 0.5f, y = cell.y + 0.5f)
        }
    }

    /**
     * Выбирает [count] комнат, разнесённых по разным углам локации (issue #13).
     *
     * Жадная выборка по принципу «самой дальней точки»: первой берём комнату,
     * максимально удалённую от спавна, затем каждую следующую — ту, что дальше
     * всего от спавна и уже выбранных комнат. Это раскидывает ключи по углам,
     * а не группирует их в соседних комнатах. Детерминировано при равном seed.
     */
    private fun spreadAcrossLocation(
        candidates: List<Pair<Room, List<GridPos>>>,
        spawn: GridPos,
        count: Int,
        random: Random,
    ): List<Pair<Room, List<GridPos>>> {
        if (candidates.size <= count) return candidates.shuffled(random)

        val remaining = candidates.toMutableList()
        val selected = ArrayList<Pair<Room, List<GridPos>>>(count)
        // Якоря, от которых отталкиваемся: спавн + центры уже выбранных комнат.
        val anchors = mutableListOf(spawn)
        while (selected.size < count && remaining.isNotEmpty()) {
            val next = remaining.maxBy { (room, _) ->
                anchors.minOf { anchor -> squaredDistance(room.center, anchor) }
            }
            selected.add(next)
            anchors.add(next.first.center)
            remaining.remove(next)
        }
        return selected
    }

    private fun squaredDistance(a: GridPos, b: GridPos): Long {
        val dx = (a.x - b.x).toLong()
        val dy = (a.y - b.y).toLong()
        return dx * dx + dy * dy
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
