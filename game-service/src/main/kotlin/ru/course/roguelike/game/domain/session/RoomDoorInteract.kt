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
    fun tryEnter(session: GameSession, pose: PlayerPose): List<GameEvent> {
        if (session.currentLevel != 0 || session.playerHp <= 0) return emptyList()
        val sealCell = DoorInteraction.findInteractable(session.map, pose) ?: return emptyList()
        val roomIndex = session.roomEngagements.indexOfFirst { sealCell in it.sealCells }
        if (roomIndex < 0) return emptyList()
        val state = session.roomEngagements[roomIndex]
        if (state.entered || state.cleared) return emptyList()
        val room = session.rooms[roomIndex]
        val doorway = RoomDoorways.doorwayForSeal(room, sealCell, state.doorways) ?: return emptyList()
        if (!RoomEngagementSystem.hasLivingMobs(session, roomIndex)) {
            state.entered = true
            state.cleared = true
            session.map.setTile(sealCell, TileType.FLOOR)
            return emptyList()
        }
        enterSealedRoom(session, roomIndex, doorway, pose)
        return emptyList()
    }

    private fun enterSealedRoom(
        session: GameSession,
        roomIndex: Int,
        doorway: GridPos,
        pose: PlayerPose,
    ) {
        val state = session.roomEngagements[roomIndex]
        val room = session.rooms[roomIndex]
        state.entered = true
        state.doorsLocked = true
        state.timerStartedAtMs = session.serverTimeMs
        RoomDoorPlacer.seal(session, state)
        session.playerPose = stepIntoRoom(session, room, doorway, pose)
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
