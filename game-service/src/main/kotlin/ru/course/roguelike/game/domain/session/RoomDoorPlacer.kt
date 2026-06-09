package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/** Расстановка дверей комнат на карту при старте сессии (issue #24). */
object RoomDoorPlacer {
    fun place(session: GameSession, startRoom: Room?) {
        val map = session.map
        session.roomEngagements.forEachIndexed { index, state ->
            val room = session.rooms.getOrNull(index) ?: return@forEachIndexed
            if (room == startRoom) {
                state.entered = true
                return@forEachIndexed
            }
            if (state.cleared || state.entered) return@forEachIndexed
            for (doorway in state.doorways) {
                if (map.get(doorway) == TileType.FLOOR) {
                    map.setTile(doorway, TileType.ROOM_DOOR)
                }
            }
        }
    }

    fun unseal(session: GameSession, state: RoomEngagementState) {
        for (cell in state.doorways) {
            if (session.map.get(cell) == TileType.ROOM_SEAL) {
                session.map.setTile(cell, TileType.FLOOR)
            }
        }
    }

    fun seal(session: GameSession, state: RoomEngagementState) {
        for (cell in state.doorways) {
            session.map.setTile(cell, TileType.ROOM_SEAL)
        }
    }
}
