package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.combat.CombatSystem
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.ItemPickupSystem
import ru.course.roguelike.game.domain.session.LevelProgressSystem
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.engine.FpsMovementSystem
import ru.course.roguelike.shared.model.PlayerPose

/** Движение и interact кооп-агента (отдельная поза, не playerPose). */
class AgentSyncInputCommand(
    private val input: InputSyncRequest,
) : GameCommand {
    override val name: String = NAME

    override fun validate(session: GameSession): CommandValidation = when {
        session.agentPose == null -> CommandValidation(false, "No coop agent in session")
        session.playerHp <= 0 -> CommandValidation(false, "Player is dead")
        else -> CommandValidation(true)
    }

    override fun execute(session: GameSession): CommandExecutionResult {
        val poseBeforeMove = session.agentPose!!
        session.agentPose = FpsMovementSystem.applyInput(session.activeMap, poseBeforeMove, input)
        session.touchClock()
        val events = mutableListOf<GameEvent>(
            GameEvent.CommandExecuted(name, accepted = true),
        )
        events.addAll(applyInteract(session, poseBeforeMove, session.agentPose!!))
        events.addAll(CombatSystem.tick(session, input.deltaMs, playerAttacking = false))
        return CommandExecutionResult(
            accepted = true,
            message = "agent sync ok",
            events = events,
        )
    }

    private fun applyInteract(
        session: GameSession,
        poseBeforeMove: PlayerPose,
        poseAfterMove: PlayerPose,
    ): List<GameEvent> {
        if (!input.interact) {
            return ItemPickupSystem.apply(session, poseAfterMove, interact = false)
        }
        val events = mutableListOf<GameEvent>()
        for (pose in listOf(poseBeforeMove, poseAfterMove)) {
            events.addAll(LevelProgressSystem.applyForPose(session, input, pose))
            events.addAll(ItemPickupSystem.apply(session, pose, interact = true))
        }
        return events
    }

    companion object {
        const val NAME = "agent_sync_input"
    }
}
