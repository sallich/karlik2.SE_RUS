package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.InventorySystem
import ru.course.roguelike.game.domain.progression.ProgressionSystem
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.hypot

/**
 * Подбор предметов на локации: патроны и аптечки — автоматически в инвентарь,
 * оружие — вручную (E), опыт — мгновенно.
 */
object ItemPickupSystem {
    private const val PICKUP_RADIUS = 0.6f
    private const val WEAPON_PICKUP_RADIUS = 0.65f
    const val EXPERIENCE_REWARD = 25

    fun apply(session: GameSession, pose: PlayerPose, interact: Boolean = false): List<GameEvent> {
        if (session.playerHp <= 0) return emptyList()

        val events = mutableListOf<GameEvent>()
        session.itemPickups
            .filter { !it.collected && canCollect(it, pose, interact) }
            .forEach { item ->
                val kindEvents = when (item.kind) {
                    ItemKind.EXPERIENCE -> {
                        item.collected = true
                        listOf(GameEvent.ItemCollected(item.id, item.kind)) +
                            ProgressionSystem.awardItemXp(session, EXPERIENCE_REWARD)
                    }
                    else -> tryCollectToInventory(session, item)
                }
                events.addAll(kindEvents)
            }
        return events
    }

    private fun tryCollectToInventory(session: GameSession, item: ItemPickup): List<GameEvent> {
        val events = InventorySystem.collectFromMap(session, item.kind, item.id).toMutableList()
        if (events.none { it is GameEvent.InventoryFull }) {
            item.collected = true
            events.add(0, GameEvent.ItemCollected(item.id, item.kind))
        }
        return events
    }

    private fun canCollect(item: ItemPickup, pose: PlayerPose, interact: Boolean): Boolean =
        if (InventoryDefinitions.isManualPickup(item.kind)) {
            interact && withinReach(item, pose, WEAPON_PICKUP_RADIUS)
        } else {
            withinReach(item, pose, PICKUP_RADIUS)
        }

    private fun withinReach(item: ItemPickup, pose: PlayerPose, radius: Float): Boolean =
        hypot((item.x - pose.x).toDouble(), (item.y - pose.y).toDouble()) <= radius
}
