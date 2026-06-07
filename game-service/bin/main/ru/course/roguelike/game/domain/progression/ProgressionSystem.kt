package ru.course.roguelike.game.domain.progression

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.InventorySystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.model.ExperienceProgression
import ru.course.roguelike.shared.model.MobKind

object ProgressionSystem {
    fun awardMobKill(session: GameSession, kind: MobKind): List<GameEvent> =
        awardExperience(session, ExperienceProgression.mobKillXp(kind), "mob_kill")

    /** Опыт за подобранный предмет (issue #9). */
    fun awardItemXp(session: GameSession, amount: Int): List<GameEvent> =
        awardExperience(session, amount, "item_pickup")

    fun checkLocationCompletion(session: GameSession): List<GameEvent> {
        if (session.locationCompletionAwarded) return emptyList()
        if (session.mobs.isNotEmpty()) return emptyList()
        session.locationCompletionAwarded = true
        val bonus = ExperienceProgression.LOCATION_COMPLETION_XP
        val events = mutableListOf<GameEvent>(GameEvent.LocationCompleted(bonus))
        events.addAll(awardExperience(session, bonus, "location_complete"))
        return events
    }

    private fun awardExperience(session: GameSession, amount: Int, source: String): List<GameEvent> {
        if (amount <= 0) return emptyList()
        val events = mutableListOf<GameEvent>()
        val oldLevel = session.playerLevel
        session.playerExperience += amount
        events.add(
            GameEvent.ExperienceGained(
                amount = amount,
                source = source,
                totalXp = session.playerExperience,
            ),
        )
        events.addAll(applyLevelUps(session, oldLevel))
        return events
    }

    private fun applyLevelUps(session: GameSession, fromLevel: Int): List<GameEvent> {
        val targetLevel = ExperienceProgression.levelFromTotalXp(session.playerExperience)
        if (targetLevel <= fromLevel) return emptyList()

        val events = mutableListOf<GameEvent>()
        for (level in fromLevel + 1..targetLevel) {
            session.playerLevel = level
            val newMaxHp = ExperienceProgression.maxHpForLevel(level)
            val hpGain = newMaxHp - session.playerMaxHp
            session.playerMaxHp = newMaxHp
            session.playerHp = (session.playerHp + hpGain).coerceAtMost(newMaxHp)
            session.playerAttackDamage = InventorySystem.recalculateAttackDamage(session)
            events.add(
                GameEvent.PlayerLevelUp(
                    newLevel = level,
                    maxHp = session.playerMaxHp,
                    attackDamage = session.playerAttackDamage,
                ),
            )
        }
        return events
    }
}
