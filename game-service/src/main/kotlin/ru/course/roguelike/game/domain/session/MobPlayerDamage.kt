package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent

/**
 * Урон от мобов по игроку: [damage] за попадание копится дробно (как у лавы),
 * HP списывается целыми единицами.
 */
object MobPlayerDamage {
    fun apply(session: GameSession, damage: Float, events: MutableList<GameEvent>) {
        if (damage <= 0f || session.playerHp <= 0) return
        session.mobDamageBuffer += damage
        val whole = session.mobDamageBuffer.toInt()
        if (whole <= 0) return
        session.mobDamageBuffer -= whole
        val before = session.playerHp
        session.playerHp = (before - whole).coerceAtLeast(0)
        events.add(GameEvent.PlayerDamaged(before - session.playerHp, session.playerHp))
    }
}
