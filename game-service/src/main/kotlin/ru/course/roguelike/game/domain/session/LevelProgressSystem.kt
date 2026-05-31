package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

object LevelProgressSystem {
    private const val KEY_PICKUP_RADIUS = 0.65f

    fun apply(session: GameSession, input: InputSyncRequest): List<GameEvent> {
        if (session.levelCompleted || session.playerHp <= 0) return emptyList()
        if (!input.interact) return emptyList()

        val events = mutableListOf<GameEvent>()
        collectKey(session, events)
        tryOpenExitGate(session, events)
        return events
    }

    private fun collectKey(session: GameSession, events: MutableList<GameEvent>) {
        val px = session.playerPose.x
        val py = session.playerPose.y
        val nearest = session.keyPickups
            .filter { !it.collected }
            .minByOrNull { hypot((it.x - px).toDouble(), (it.y - py).toDouble()) }
            ?: return

        if (hypot((nearest.x - px).toDouble(), (nearest.y - py).toDouble()) > KEY_PICKUP_RADIUS) return

        nearest.collected = true
        events.add(
            GameEvent.KeyCollected(
                keyId = nearest.id,
                totalCollected = session.keysCollected,
            ),
        )
    }

    private fun tryOpenExitGate(session: GameSession, events: MutableList<GameEvent>) {
        val gate = session.exitGate ?: return
        if (session.keysCollected < session.keysRequired) return

        val cell = playerCell(session)
        if (cell != gate) return
        if (session.activeMap.get(gate) != TileType.EXIT_GATE) return

        session.levelCompleted = true
        events.add(GameEvent.LevelCompleted(session.keysCollected, session.keysRequired))
    }

    private fun playerCell(session: GameSession): GridPos =
        GridPos(floor(session.playerPose.x).toInt(), floor(session.playerPose.y).toInt())
}
