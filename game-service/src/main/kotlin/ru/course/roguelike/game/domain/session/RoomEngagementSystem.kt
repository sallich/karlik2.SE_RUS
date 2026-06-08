package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot

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
