package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.combat.CombatSystem
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.InventorySystem
import ru.course.roguelike.game.domain.session.ElevatorSystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.InteractSystem
import ru.course.roguelike.game.domain.session.LavaDamageSystem
import ru.course.roguelike.game.domain.session.PlayerVerticalMotion
import ru.course.roguelike.game.domain.session.RoomEngagementSystem
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.FpsMovementSystem

/** Пошаговая обработка FPS-sync игрока (движение, interact, инвентарь, бой). */
internal object SyncInputPipeline {
    fun runPlayer(session: GameSession, input: InputSyncRequest): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        ElevatorSystem.apply(session, input.deltaMs)

        val poseBeforeMove = session.playerPose
        PlayerVerticalMotion.applyJump(session, input)
        session.playerPose = FpsMovementSystem.applyInput(session.activeMap, session.playerPose, input)
        session.touchClock()

        events.add(GameEvent.PlayerMoved(session.tick))
        LavaDamageSystem.apply(session, input.deltaMs)?.let { events.add(it) }
        events.addAll(InteractSystem.apply(session, input, poseBeforeMove, session.playerPose))
        events.addAll(InventorySystem.handleHotbarInput(session, input.hotbarSelect, input.hotbarAssign, input.reload))
        events.addAll(InventorySystem.handleInventoryInput(session, input.inventoryCycle, input.inventoryDrop))
        RoomEngagementSystem.tick(session)
        events.addAll(CombatSystem.tick(session, input.deltaMs, input.attack))
        return events
    }

    fun runAgent(session: GameSession, input: InputSyncRequest): List<GameEvent> {
        val poseBeforeMove = session.agentPose!!
        session.agentPose = FpsMovementSystem.applyInput(session.activeMap, poseBeforeMove, input)
        session.touchClock()

        val events = mutableListOf<GameEvent>()
        events.addAll(InteractSystem.apply(session, input, poseBeforeMove, session.agentPose!!))
        events.addAll(CombatSystem.tick(session, input.deltaMs, playerAttacking = false))
        return events
    }
}
