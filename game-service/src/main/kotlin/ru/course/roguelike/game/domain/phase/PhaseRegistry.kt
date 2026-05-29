package ru.course.roguelike.game.domain.phase

import ru.course.roguelike.shared.model.SessionPhase

class PhaseRegistry(
    private val handlers: Map<SessionPhase, PhaseHandler> = defaultHandlers(),
) {
    fun handlerFor(phase: SessionPhase): PhaseHandler =
        handlers[phase] ?: error("No handler for phase $phase")

    companion object {
        fun defaultHandlers(): Map<SessionPhase, PhaseHandler> = listOf(
            ExplorationPhaseHandler(),
            CombatPhaseHandler(),
            ChoicePhaseHandler(),
            HubPhaseHandler(),
            GameOverPhaseHandler(),
        ).associateBy { it.phase }
    }
}
