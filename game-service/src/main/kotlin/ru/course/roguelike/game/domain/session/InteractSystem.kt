package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.model.PlayerPose

/**
 * E (interact): проверка до и после шага движения в батче —
 * иначе при удержании WASD игрок успевает выйти из радиуса.
 */
object InteractSystem {
    fun apply(
        session: GameSession,
        input: InputSyncRequest,
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
}
