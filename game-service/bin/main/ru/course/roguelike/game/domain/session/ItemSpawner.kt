package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import kotlin.random.Random

/** Предмет, лежащий на локации, который герой подбирает, проходя рядом (issue #9). */
data class ItemPickup(
    val id: Int,
    val kind: ItemKind,
    val x: Float,
    val y: Float,
    var collected: Boolean = false,
) {
    fun toSnapshot() = ItemSnapshot(id = id, kind = kind, x = x, y = y)
}

/**
 * Случайная расстановка предметов по комнатам локации (issue #9).
 *
 * Каждая подходящая комната с вероятностью [ROOM_SPAWN_CHANCE] получает один
 * предмет случайного вида. Детерминировано при равном seed. Ячейки, занятые
 * другими сущностями (ключи, спавн), исключаются через [occupied].
 */
object ItemSpawner {
    /** Шанс, что в очередной комнате появится предмет. */
    private const val ROOM_SPAWN_CHANCE = 0.45

    /** Распределение видов предметов на локации (сумма весов не важна). */
    private val LOCATION_WEIGHTS = mapOf(
        ItemKind.HEALTH to 4,
        ItemKind.AMMO_PISTOL to 3,
        ItemKind.AMMO_SHOTGUN to 2,
        ItemKind.EXPERIENCE to 2,
        ItemKind.WEAPON_PISTOL to 3,
        ItemKind.WEAPON_SHOTGUN to 1,
    )

    fun spawn(
        level: GeneratedLevel,
        seed: Long,
        occupied: Set<GridPos> = emptySet(),
        startId: Int = 0,
    ): List<ItemPickup> {
        val safeCells = MapConnectivity.reachableSafeFloorCells(level.map, level.playerSpawn)
        val random = Random(seed xor ITEM_SALT)
        val items = mutableListOf<ItemPickup>()
        var nextId = startId

        for (room in level.rooms.filter { !it.isBoss }) {
            val roll = random.nextDouble()
            val cells = roomCells(room, safeCells).filter { it !in occupied }
            if (roll < ROOM_SPAWN_CHANCE && cells.isNotEmpty()) {
                val cell = cells[random.nextInt(cells.size)]
                items.add(
                    ItemPickup(
                        id = nextId++,
                        kind = randomKind(random),
                        x = cell.x + 0.5f,
                        y = cell.y + 0.5f,
                    ),
                )
            }
        }
        return items
    }

    /** Случайный вид с учётом весов [LOCATION_WEIGHTS]. */
    fun randomKind(random: Random): ItemKind {
        val total = LOCATION_WEIGHTS.values.sum()
        var roll = random.nextInt(total)
        for ((kind, weight) in LOCATION_WEIGHTS) {
            if (roll < weight) return kind
            roll -= weight
        }
        return ItemKind.HEALTH
    }

    private fun roomCells(
        room: ru.course.roguelike.game.domain.level.Room,
        safeCells: Set<GridPos>,
    ): List<GridPos> {
        val cells = mutableListOf<GridPos>()
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                val pos = GridPos(x, y)
                if (pos in safeCells) cells.add(pos)
            }
        }
        return cells
    }

    private const val ITEM_SALT = 0x1_7E_05L
}
