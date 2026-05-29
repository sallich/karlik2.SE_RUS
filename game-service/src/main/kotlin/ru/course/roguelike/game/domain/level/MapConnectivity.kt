package ru.course.roguelike.game.domain.level

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos

/**
 * Проверка проходимости карты (issue #3: «не сделать непроходимую комнату»).
 *
 * Используется и при генерации (как гарантия связности после декорирования),
 * и в тестах. Проходимыми считаются тайлы с [TileType.walkable] == true
 * (FLOOR и LAVA), стены и колонны — препятствия.
 */
object MapConnectivity {
    private val NEIGHBOR_OFFSETS = listOf(
        GridPos(1, 0),
        GridPos(-1, 0),
        GridPos(0, 1),
        GridPos(0, -1),
    )

    /** BFS по 4-связным проходимым тайлам от [start]. Возвращает все достижимые позиции. */
    fun reachableFrom(map: TileMap, start: GridPos): Set<GridPos> {
        val visited = HashSet<GridPos>()
        if (!map.isWalkable(start)) return visited

        val queue = ArrayDeque<GridPos>()
        visited.add(start)
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (offset in NEIGHBOR_OFFSETS) {
                val next = GridPos(cur.x + offset.x, cur.y + offset.y)
                if (next !in visited && map.isWalkable(next)) {
                    visited.add(next)
                    queue.addLast(next)
                }
            }
        }
        return visited
    }

    /**
     * Комнаты, у которых хотя бы один проходимый тайл недостижим от [start].
     * Пустой список — все комнаты полностью проходимы со спавна.
     */
    fun unreachableRooms(map: TileMap, start: GridPos, rooms: List<Room>): List<Room> {
        val reachable = reachableFrom(map, start)
        return rooms.filterNot { roomFullyReachable(map, it, reachable) }
    }

    /** Достижима ли каждая комната (включая комнату-босс) целиком от спавна. */
    fun allRoomsReachable(map: TileMap, start: GridPos, rooms: List<Room>): Boolean =
        unreachableRooms(map, start, rooms).isEmpty()

    /** Все ли проходимые тайлы карты образуют единую связную область от [start]. */
    fun isFullyConnected(map: TileMap, start: GridPos): Boolean {
        val reachable = reachableFrom(map, start)
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val pos = GridPos(x, y)
                if (map.isWalkable(pos) && pos !in reachable) return false
            }
        }
        return true
    }

    /**
     * Комната проходима, если все её walkable-тайлы достижимы от спавна.
     * Непроходимые тайлы (стены, колонны) допустимо иметь недостижимыми —
     * проверка ловит именно изолированные «карманы» пола/лавы.
     */
    private fun roomFullyReachable(map: TileMap, room: Room, reachable: Set<GridPos>): Boolean {
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                val pos = GridPos(x, y)
                if (map.isWalkable(pos) && pos !in reachable) return false
            }
        }
        return true
    }
}
