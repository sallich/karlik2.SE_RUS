package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.shared.model.FpsConstants

/**
 * Урон от лавы (issue #3): пока герой стоит на тайле LAVA, HP убывает со
 * скоростью [FpsConstants.LAVA_DAMAGE_PER_SECOND]. Интервал sync (~50 мс) даёт
 * дробный урон за тик, поэтому он копится в [GameSession.lavaDamageBuffer] и
 * списывается целыми единицами. Сход с лавы сбрасывает накопитель.
 *
 * Переход в GAME_OVER при HP == 0 выполняет фаза-обработчик (см. PhaseHandler).
 */
object LavaDamageSystem {
    /**
     * Применяет урон лавой за прошедшие [deltaMs]. Возвращает событие
     * [GameEvent.PlayerDamaged], если урон был нанесён, иначе null.
     */
    fun apply(session: GameSession, deltaMs: Int): GameEvent? {
        val tile = session.activeMap.getTileAt(session.playerPose.x, session.playerPose.y)
        if (tile?.damaging != true) {
            session.lavaDamageBuffer = 0f
            return null
        }

        session.lavaDamageBuffer += FpsConstants.LAVA_DAMAGE_PER_SECOND * deltaMs / MILLIS_PER_SECOND
        val damage = session.lavaDamageBuffer.toInt()
        if (damage <= 0) return null

        session.lavaDamageBuffer -= damage
        val before = session.playerHp
        session.playerHp = (before - damage).coerceAtLeast(0)
        return GameEvent.PlayerDamaged(amount = before - session.playerHp, remainingHp = session.playerHp)
    }

    private const val MILLIS_PER_SECOND = 1000f
}
