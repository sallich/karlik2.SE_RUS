package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.DoorInteraction
import ru.course.roguelike.shared.engine.PlayerPlacement
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.hypot

/** Вход в комнату по E через красную печать в коридоре (issue #24). */
object RoomDoorInteract {
    private data class RoomEntry(
        val roomIndex: Int,
        val sealCell: GridPos,
        val doorway: GridPos,
    )

    fun tryEnter(session: GameSession, pose: PlayerPose): List<GameEvent> {
        val entry = resolveEntry(session, pose) ?: return emptyList()
        if (!RoomEngagementSystem.hasLivingMobs(session, entry.roomIndex)) {
            clearEmptyRoom(session, entry)
            return emptyList()
        }
        enterSealedRoom(session, entry, pose)
        return emptyList()
    }

    private fun resolveEntry(session: GameSession, pose: PlayerPose): RoomEntry? {
        if (session.currentLevel != 0 || session.playerHp <= 0) return null
        val sealCell = DoorInteraction.findInteractable(session.map, pose) ?: return null
        val roomIndex = session.roomEngagements.indexOfFirst { sealCell in it.sealCells }
        val state = roomIndex.takeIf { it >= 0 }?.let { session.roomEngagements[it] }
        if (state == null || state.entered || state.cleared) return null
        val room = session.rooms[roomIndex]
        return RoomDoorways.doorwayForSeal(room, sealCell, state.doorways)?.let {
            RoomEntry(roomIndex, sealCell, it)
        }
    }

    private fun clearEmptyRoom(session: GameSession, entry: RoomEntry) {
        val state = session.roomEngagements[entry.roomIndex]
        state.entered = true
        state.cleared = true
        session.map.setTile(entry.sealCell, TileType.FLOOR)
    }

    private fun enterSealedRoom(
        session: GameSession,
        entry: RoomEntry,
        pose: PlayerPose,
    ) {
        val state = session.roomEngagements[entry.roomIndex]
        val room = session.rooms[entry.roomIndex]
        state.entered = true
        state.doorsLocked = true
        state.timerStartedAtMs = session.serverTimeMs
        RoomDoorPlacer.seal(session, state)
        session.playerPose = stepIntoRoom(session, room, entry.doorway, pose)
    }

    private fun stepIntoRoom(
        session: GameSession,
        room: Room,
        doorway: GridPos,
        pose: PlayerPose,
    ): PlayerPose {
        val map = session.map
        val doorX = doorway.x + 0.5f
        val doorY = doorway.y + 0.5f
        val inwardX = room.center.x + 0.5f - doorX
        val inwardY = room.center.y + 0.5f - doorY
        val inwardLen = hypot(inwardX.toDouble(), inwardY.toDouble()).toFloat().coerceAtLeast(0.001f)
        val targetX = doorX + inwardX / inwardLen * 1.1f
        val targetY = doorY + inwardY / inwardLen * 1.1f
        val (safeX, safeY) = PlayerPlacement.resolve(
            map = map,
            preferredX = targetX,
            preferredY = targetY,
            localHeight = pose.height,
            searchBounds = { room.contains(it) },
        )
        return pose.copy(x = safeX, y = safeY)
    }
}
