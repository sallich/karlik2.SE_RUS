package ru.course.roguelike.game.domain.level

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/**
 * Проверка проходимости карты (issue #3: «не сделать непроходимую комнату»).
 *
 * Используется и при генерации (как гарантия связности после декорирования),
 * и в тестах. Проходимыми считаются тайлы с [TileType.walkable] == true
 * (FLOOR и LAVA), стены и колонны — препятствия.
 */
object MapConnectivity {
    /** BFS по 4-связным проходимым тайлам от [start]. Возвращает все достижимые позиции. */
    fun reachableFrom(map: TileMap, start: GridPos): Set<GridPos> {
        val visited = HashSet<GridPos>()
        if (!map.isWalkable(start)) return visited

        val queue = ArrayDeque<GridPos>()
        visited.add(start)
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (offset in MapConnectivityNeighborOffsets) {
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

    /** Проходимый пол (не лава), достижимый от [start] — безопасная зона для ключей и лута. */
    fun reachableSafeFloorCells(map: TileMap, start: GridPos): Set<GridPos> {
        val reachable = reachableFrom(map, start)
        return reachable.filterTo(mutableSetOf()) { map.get(it) == TileType.FLOOR }
    }

    /**
     * Комнаты, достижимые из [room] по коридорам без прохода через интерьеры других комнат.
     * Используется для подкреплений при истечении таймера зачистки.
     */
    fun adjacentRooms(map: TileMap, room: Room, allRooms: List<Room>): List<Room> {
        if (allRooms.none { it != room }) return emptyList()

        val found = linkedSetOf<Room>()
        val roomAt = { pos: GridPos -> allRooms.firstOrNull { it.contains(pos) } }
        for (seed in corridorSeeds(map, room)) {
            found.addAll(roomsBeyondSeed(map, room, seed, roomAt))
        }
        return found.toList()
    }

    private fun corridorSeeds(map: TileMap, room: Room): Set<GridPos> {
        val seeds = mutableSetOf<GridPos>()
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                collectCorridorSeedsForCell(map, room, GridPos(x, y), seeds)
            }
        }
        return seeds
    }

    private fun roomsBeyondSeed(
        map: TileMap,
        room: Room,
        seed: GridPos,
        roomAt: (GridPos) -> Room?,
    ): Set<Room> {
        val found = linkedSetOf<Room>()
        val visited = mutableSetOf<GridPos>()
        val queue = ArrayDeque<GridPos>()
        visited.add(seed)
        queue.addLast(seed)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val owner = roomAt(cur)
            if (owner != null && owner != room) {
                found.add(owner)
            } else {
                expandCorridor(map, room, cur, visited, queue, found, roomAt)
            }
        }
        return found
    }

    private fun expandCorridor(
        map: TileMap,
        room: Room,
        cur: GridPos,
        visited: MutableSet<GridPos>,
        queue: ArrayDeque<GridPos>,
        found: MutableSet<Room>,
        roomAt: (GridPos) -> Room?,
    ) {
        for (offset in MapConnectivityNeighborOffsets) {
            val next = GridPos(cur.x + offset.x, cur.y + offset.y)
            if (next in visited || !map.isWalkable(next)) continue
            val nextOwner = roomAt(next)
            if (nextOwner != null && nextOwner != room) {
                found.add(nextOwner)
            } else {
                visited.add(next)
                queue.addLast(next)
            }
        }
    }
}

private fun collectCorridorSeedsForCell(
    map: TileMap,
    room: Room,
    pos: GridPos,
    seeds: MutableSet<GridPos>,
) {
    if (!map.isWalkable(pos)) return
    for (offset in MapConnectivityNeighborOffsets) {
        val neighbor = GridPos(pos.x + offset.x, pos.y + offset.y)
        if (map.isWalkable(neighbor) && !room.contains(neighbor)) {
            seeds.add(neighbor)
        }
    }
}

private val MapConnectivityNeighborOffsets = listOf(
    GridPos(1, 0),
    GridPos(-1, 0),
    GridPos(0, 1),
    GridPos(0, -1),
)
