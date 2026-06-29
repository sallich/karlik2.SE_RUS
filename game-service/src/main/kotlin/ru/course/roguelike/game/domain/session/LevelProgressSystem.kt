package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.InteractionConstants
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

object LevelProgressSystem {
    fun apply(session: GameSession, input: InputSyncRequest): List<GameEvent> =
        applyForPose(session, input, session.playerPose)

    fun applyForPose(session: GameSession, input: InputSyncRequest, pose: PlayerPose): List<GameEvent> {
        if (session.levelCompleted || session.playerHp <= 0) return emptyList()
        if (!input.interact) return emptyList()

        val events = mutableListOf<GameEvent>()
        collectKey(session, pose, events)
        tryOpenExitGate(session, pose, events)
        return events
    }

    private fun collectKey(session: GameSession, pose: PlayerPose, events: MutableList<GameEvent>) {
        val px = pose.x
        val py = pose.y
        val nearest = session.keyPickups
            .filter { !it.collected && RoomVisibility.isKeyVisible(session, it) }
            .minByOrNull { hypot((it.x - px).toDouble(), (it.y - py).toDouble()) }
            ?: return

        if (hypot((nearest.x - px).toDouble(), (nearest.y - py).toDouble()) > InteractionConstants.INTERACT_RADIUS) {
            return
        }

        nearest.collected = true
        events.add(
            GameEvent.KeyCollected(
                keyId = nearest.id,
                totalCollected = session.keysCollected,
            ),
        )
    }

    private fun tryOpenExitGate(session: GameSession, pose: PlayerPose, events: MutableList<GameEvent>) {
        val gate = session.exitGate ?: return
        if (session.keysCollected < session.keysRequired) return

        val cell = playerCell(pose)
        if (cell != gate) return
        if (session.activeMap.get(gate) != TileType.EXIT_GATE) return

        session.levelCompleted = true
        events.add(GameEvent.LevelCompleted(session.keysCollected, session.keysRequired))
    }

    private fun playerCell(pose: PlayerPose): GridPos =
        GridPos(floor(pose.x).toInt(), floor(pose.y).toInt())
}
