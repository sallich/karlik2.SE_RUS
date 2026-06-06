package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.combat.CombatSystem
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.InventorySystem
import ru.course.roguelike.game.domain.session.ElevatorSystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.ItemPickupSystem
import ru.course.roguelike.game.domain.session.LavaDamageSystem
import ru.course.roguelike.game.domain.session.LevelProgressSystem
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.ElevatorPhase
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.engine.VerticalMotion
import ru.course.roguelike.shared.model.FpsConstants
import kotlin.math.ceil

class SyncInputCommand(
    private val input: InputSyncRequest,
) : GameCommand {
    override val name: String = NAME

    override fun validate(session: GameSession): CommandValidation =
        if (session.playerHp <= 0) {
            CommandValidation(false, "Player is dead")
        } else {
            CommandValidation(true)
        }

    override fun execute(session: GameSession): CommandExecutionResult {
        val levelEvent = ElevatorSystem.apply(session, input.deltaMs)
        applyVerticalMotion(session, input)
        session.playerPose = FpsMovementSystem.applyInput(session.activeMap, session.playerPose, input)
        session.touchClock()
        val events = mutableListOf<GameEvent>(
            GameEvent.CommandExecuted(name, accepted = true),
            GameEvent.PlayerMoved(session.tick),
        )
        LavaDamageSystem.apply(session, input.deltaMs)?.let { events.add(it) }
        levelEvent?.let { events.add(it) }
        events.addAll(LevelProgressSystem.apply(session, input))
        events.addAll(ItemPickupSystem.apply(session, session.playerPose, input.interact))
        events.addAll(InventorySystem.handleHotbarInput(session, input.hotbarSelect, input.hotbarAssign, input.reload))
        events.addAll(CombatSystem.tick(session, input.deltaMs, input.attack))
        return CommandExecutionResult(
            accepted = true,
            message = "sync ok",
            events = events,
        )
    }

    companion object {
        const val NAME = "sync_input"

        private fun applyVerticalMotion(session: GameSession, input: InputSyncRequest) {
            if (session.elevatorPhase != ElevatorPhase.IDLE) return
            applyJump(session, input)
        }

        private fun applyJump(session: GameSession, input: InputSyncRequest) {
            val totalMs = input.deltaMs.coerceIn(1, 250)
            val steps = ceil(totalMs.toFloat() / FpsConstants.MOVEMENT_SUBSTEP_MS).toInt()
                .coerceIn(1, 12)
            val msPerStep = totalMs / steps
            var h = session.playerPose.height
            var v = session.playerVerticalVelocity
            var jumpPending = input.jump
            val map = session.activeMap
            val x = session.playerPose.x
            val y = session.playerPose.y
            repeat(steps) {
                val state = VerticalMotion.tick(
                    map = map,
                    x = x,
                    y = y,
                    height = h,
                    verticalVelocity = v,
                    jumpRequested = jumpPending,
                    deltaMs = msPerStep,
                )
                h = state.height
                v = state.verticalVelocity
                jumpPending = false
            }
            session.playerVerticalVelocity = v
            session.playerPose = session.playerPose.copy(height = h)
        }
    }
}
