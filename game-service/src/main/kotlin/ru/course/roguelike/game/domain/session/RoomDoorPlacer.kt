package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType

/** Расстановка печатей комнат на карту при старте сессии (issue #24). */
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
            for (sealCell in state.sealCells) {
                if (map.get(sealCell) == TileType.FLOOR) {
                    map.setTile(sealCell, TileType.ROOM_SEAL)
                }
            }
        }
    }

    fun unseal(session: GameSession, state: RoomEngagementState) {
        for (cell in state.sealCells) {
            if (session.map.get(cell) == TileType.ROOM_SEAL) {
                session.map.setTile(cell, TileType.FLOOR)
            }
        }
    }

    fun seal(session: GameSession, state: RoomEngagementState) {
        for (cell in state.sealCells) {
            session.map.setTile(cell, TileType.ROOM_SEAL)
        }
    }
}
