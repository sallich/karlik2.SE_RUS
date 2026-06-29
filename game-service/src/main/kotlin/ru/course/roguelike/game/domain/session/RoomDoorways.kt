package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos

/**
 * Поиск дверных проёмов комнаты (issue #24).
 *
 * Проём — проходимая ячейка на периметре комнаты с выходом в коридор.
 * Красная печать ([TileType.ROOM_SEAL]) ставится на соседнюю коридорную клетку
 * ([corridorSealOf], [sealCells]); проём внутри комнаты остаётся полом.
 */
object RoomDoorways {
    private val NEIGHBORS = listOf(
        GridPos(1, 0),
        GridPos(-1, 0),
        GridPos(0, 1),
        GridPos(0, -1),
    )

    fun of(map: TileMap, room: Room): List<GridPos> {
        val doorways = mutableListOf<GridPos>()
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                val pos = GridPos(x, y)
                if (!map.isWalkable(pos)) continue
                if (isPerimeter(room, pos) && leadsOutside(map, room, pos)) {
                    doorways.add(pos)
                }
            }
        }
        return doorways
    }

    /** Ячейка коридора, куда ставится печать при запирании (снаружи комнаты). */
    fun corridorSealOf(map: TileMap, room: Room, doorway: GridPos): GridPos? =
        NEIGHBORS.map { GridPos(doorway.x + it.x, doorway.y + it.y) }
            .firstOrNull { neighbor -> !room.contains(neighbor) && map.isWalkable(neighbor) }

    fun sealCells(map: TileMap, room: Room, doorways: List<GridPos>): List<GridPos> =
        doorways.mapNotNull { corridorSealOf(map, room, it) }

    /** Проём внутри комнаты, связанный с коридорной печатью. */
    fun doorwayForSeal(room: Room, sealCell: GridPos, doorways: List<GridPos>): GridPos? =
        NEIGHBORS.map { GridPos(sealCell.x + it.x, sealCell.y + it.y) }
            .firstOrNull { room.contains(it) && it in doorways }

    private fun isPerimeter(room: Room, pos: GridPos): Boolean =
        pos.x == room.x || pos.x == room.x + room.width - 1 ||
            pos.y == room.y || pos.y == room.y + room.height - 1

    private fun leadsOutside(map: TileMap, room: Room, pos: GridPos): Boolean =
        NEIGHBORS.any { offset ->
            val neighbor = GridPos(pos.x + offset.x, pos.y + offset.y)
            !room.contains(neighbor) && map.isWalkable(neighbor)
        }
}
