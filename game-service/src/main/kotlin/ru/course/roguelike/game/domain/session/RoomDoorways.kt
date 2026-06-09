package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos

/**
 * Поиск дверных проёмов комнаты (issue #24).
 *
 * Проём — это проходимая ячейка на периметре комнаты, у которой есть проходимый
 * сосед за пределами комнаты (то есть выход в коридор). Именно эти ячейки запираются,
 * пока комната не зачищена. Логика зеркальна сбору коридорных «семян» в
 * [ru.course.roguelike.game.domain.level.MapConnectivity], но возвращает ячейку внутри комнаты.
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

    private fun isPerimeter(room: Room, pos: GridPos): Boolean =
        pos.x == room.x || pos.x == room.x + room.width - 1 ||
            pos.y == room.y || pos.y == room.y + room.height - 1

    private fun leadsOutside(map: TileMap, room: Room, pos: GridPos): Boolean =
        NEIGHBORS.any { offset ->
            val neighbor = GridPos(pos.x + offset.x, pos.y + offset.y)
            !room.contains(neighbor) && map.isWalkable(neighbor)
        }
}
