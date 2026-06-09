package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

/**
 * Таймер зачистки комнаты: при входе стартует отсчёт; если за [RoomEngagementConstants.CLEAR_TIMER_MS]
 * в комнате остаются живые мобы, соседние по лабиринту комнаты отправляют своих мобов к ней.
 */
object RoomEngagementSystem {
    fun tick(session: GameSession) {
        if (session.rooms.isEmpty() || session.roomEngagements.isEmpty()) return

        refreshClearedStates(session)

        val playerRoom = findPlayerRoom(session)
        if (playerRoom != null) {
            val index = session.rooms.indexOf(playerRoom)
            if (index >= 0) {
                maybeStartTimer(session, index)
            }
        }

        for (state in session.roomEngagements) {
            if (state.cleared || state.reinforcementsTriggered || state.timerStartedAtMs == null) continue
            val elapsed = session.serverTimeMs - state.timerStartedAtMs!!
            if (elapsed >= RoomEngagementConstants.CLEAR_TIMER_MS) {
                state.reinforcementsTriggered = true
                triggerReinforcements(session, state.roomIndex)
            }
        }

        applyDoorLocks(session)
    }

    private fun refreshClearedStates(session: GameSession) {
        for (state in session.roomEngagements) {
            if (state.cleared) continue
            if (!hasLivingMobs(session, state.roomIndex)) {
                state.cleared = true
                clearReinforcementsTargeting(session, state.roomIndex)
            }
        }
    }

    private fun maybeStartTimer(session: GameSession, roomIndex: Int) {
        val state = session.roomEngagements.getOrNull(roomIndex) ?: return
        if (state.cleared || state.timerStartedAtMs != null) return
        if (!hasLivingMobs(session, roomIndex)) return
        state.timerStartedAtMs = session.serverTimeMs
        state.doorsLocked = true
    }

    /**
     * Запирает двери комнат с активным боем и открывает их после зачистки (issue #24).
     * Ячейку, на которой сейчас стоит герой, не запираем — иначе он застрянет в стене;
     * она запрётся на следующем тике, как только герой сойдёт с неё.
     */
    private fun applyDoorLocks(session: GameSession) {
        val map = session.activeMap
        val playerCell = GridPos(floor(session.playerPose.x).toInt(), floor(session.playerPose.y).toInt())
        session.roomEngagements.forEach { updateRoomDoors(map, it, playerCell) }
    }

    private fun updateRoomDoors(map: TileMap, state: RoomEngagementState, playerCell: GridPos) {
        if (state.doorways.isEmpty()) return
        if (state.cleared) {
            if (state.doorsLocked) {
                state.doorways.forEach { map.setTile(it, TileType.FLOOR) }
                state.doorsLocked = false
            }
            return
        }
        if (!state.doorsLocked) return
        // Запираем все проёмы, но не «замуровываем» героя — клетку под ним оставляем полом.
        state.doorways.forEach { cell ->
            when {
                cell != playerCell -> map.setTile(cell, TileType.DOOR_LOCKED)
                map.get(cell) == TileType.DOOR_LOCKED -> map.setTile(cell, TileType.FLOOR)
            }
        }
    }

    private fun hasLivingMobs(session: GameSession, roomIndex: Int): Boolean {
        val room = session.rooms.getOrNull(roomIndex) ?: return false
        return session.mobs.any { it.alive && it.aggroRoom == room }
    }

    private fun triggerReinforcements(session: GameSession, targetIndex: Int) {
        val targetRoom = session.rooms.getOrNull(targetIndex) ?: return
        val map = session.activeMap
        val adjacent = MapConnectivity.adjacentRooms(map, targetRoom, session.rooms)
        for (sourceRoom in adjacent) {
            for (mob in session.mobs) {
                if (!mob.alive || mob.aggroRoom != sourceRoom) continue
                mob.reinforceTarget = targetRoom
            }
        }
    }

    private fun clearReinforcementsTargeting(session: GameSession, roomIndex: Int) {
        val room = session.rooms.getOrNull(roomIndex) ?: return
        for (mob in session.mobs) {
            if (mob.reinforceTarget == room) {
                mob.reinforceTarget = null
            }
        }
    }

    fun findPlayerRoom(session: GameSession): Room? =
        session.rooms.firstOrNull { it.containsWorld(session.playerPose.x, session.playerPose.y) }

    fun timerSnapshot(session: GameSession): RoomClearTimerSnapshot? {
        val state = findPlayerRoom(session)
            ?.let { session.rooms.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.let { session.roomEngagements.getOrNull(it) }
            ?: return null
        if (state.cleared || state.timerStartedAtMs == null) return null

        val elapsed = session.serverTimeMs - state.timerStartedAtMs!!
        val remaining = (RoomEngagementConstants.CLEAR_TIMER_MS - elapsed).coerceAtLeast(0)
        return RoomClearTimerSnapshot(
            remainingMs = remaining,
            totalMs = RoomEngagementConstants.CLEAR_TIMER_MS,
            reinforcementsTriggered = state.reinforcementsTriggered,
        )
    }
}
