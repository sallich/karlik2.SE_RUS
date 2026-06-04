package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.progression.ProgressionSystem
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.hypot

/**
 * Подбор предметов на локации (issue #9): герой автоматически собирает предмет,
 * проходя рядом с ним, и сразу получает его эффект.
 */
object ItemPickupSystem {
    private const val PICKUP_RADIUS = 0.6f

    /** На сколько предмет здоровья восстанавливает HP. */
    const val HEALTH_RESTORE = 30

    /** Сколько опыта даёт предмет опыта. */
    const val EXPERIENCE_REWARD = 25

    /** Постоянная прибавка к урону от предмета-оружия. */
    const val WEAPON_DAMAGE_BONUS = 5

    /** Сколько патронов даёт предмет с боезапасом. */
    const val AMMO_REFILL = 20

    fun apply(session: GameSession, pose: PlayerPose): List<GameEvent> {
        if (session.playerHp <= 0) return emptyList()

        val events = mutableListOf<GameEvent>()
        session.itemPickups
            .filter { !it.collected && withinReach(it, pose) }
            .forEach { item ->
                item.collected = true
                events.add(GameEvent.ItemCollected(item.id, item.kind))
                events.addAll(applyEffect(session, item.kind))
            }
        return events
    }

    private fun withinReach(item: ItemPickup, pose: PlayerPose): Boolean =
        hypot((item.x - pose.x).toDouble(), (item.y - pose.y).toDouble()) <= PICKUP_RADIUS

    private fun applyEffect(session: GameSession, kind: ItemKind): List<GameEvent> = when (kind) {
        ItemKind.HEALTH -> healPlayer(session)
        ItemKind.EXPERIENCE -> ProgressionSystem.awardItemXp(session, EXPERIENCE_REWARD)
        ItemKind.WEAPON -> upgradeWeapon(session)
        ItemKind.AMMO -> refillAmmo(session)
    }

    private fun healPlayer(session: GameSession): List<GameEvent> {
        val before = session.playerHp
        session.playerHp = (before + HEALTH_RESTORE).coerceAtMost(session.playerMaxHp)
        val healed = session.playerHp - before
        return if (healed > 0) {
            listOf(GameEvent.PlayerHealed(healed, session.playerHp))
        } else {
            emptyList()
        }
    }

    private fun upgradeWeapon(session: GameSession): List<GameEvent> {
        session.playerWeaponBonus += WEAPON_DAMAGE_BONUS
        session.playerAttackDamage += WEAPON_DAMAGE_BONUS
        return listOf(GameEvent.WeaponUpgraded(WEAPON_DAMAGE_BONUS, session.playerAttackDamage))
    }

    private fun refillAmmo(session: GameSession): List<GameEvent> {
        val before = session.playerAmmo
        session.playerAmmo = (before + AMMO_REFILL).coerceAtMost(CombatConstants.PLAYER_MAX_AMMO)
        val gained = session.playerAmmo - before
        return if (gained > 0) {
            listOf(GameEvent.AmmoChanged(gained, session.playerAmmo))
        } else {
            emptyList()
        }
    }
}
