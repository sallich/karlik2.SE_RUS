package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.dto.KeySnapshot
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
    private const val MIN_ROOM_DISTANCE = 2

    fun spawn(level: GeneratedLevel, seed: Long, count: Int = DEFAULT_KEY_COUNT): List<KeyPickup> {
        val candidates = level.rooms.filter { !it.isBoss }
        if (candidates.isEmpty()) return emptyList()

        val random = Random(seed xor KEY_SALT)
        val chosenRooms = candidates.shuffled(random).take(count.coerceAtMost(candidates.size))
        return chosenRooms.mapIndexed { index, room ->
            KeyPickup(
                id = index,
                x = room.x + room.width / 2f + randomOffset(random),
                y = room.y + room.height / 2f + randomOffset(random),
            )
        }
    }

    fun bossRoomOf(level: GeneratedLevel): Room? = level.rooms.singleOrNull { it.isBoss }

    private fun randomOffset(random: Random): Float =
        (random.nextInt(-MIN_ROOM_DISTANCE, MIN_ROOM_DISTANCE + 1)) * 0.35f

    private const val KEY_SALT = 0x4B_45_59_53L
}
