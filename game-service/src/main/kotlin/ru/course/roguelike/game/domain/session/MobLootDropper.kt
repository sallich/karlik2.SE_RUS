package ru.course.roguelike.game.domain.session

import ru.course.roguelike.game.domain.combat.MobEntity
import ru.course.roguelike.game.domain.event.GameEvent
import kotlin.random.Random

/**
 * Выпадение предметов с убитых мобов (issue #9): с вероятностью [MOB_DROP_CHANCE]
 * моб оставляет предмет случайного вида на месте гибели. Детерминировано при
 * равных seed/tick/id моба.
 */
object MobLootDropper {
    private const val MOB_DROP_CHANCE = 0.35
    private const val DROP_SALT = 0x9E_37_79_B9L

    fun dropFrom(session: GameSession, mob: MobEntity): GameEvent? {
        val random = Random(session.seed xor (mob.id * DROP_SALT) xor session.tick)
        if (random.nextDouble() >= MOB_DROP_CHANCE) return null
        val item = ItemPickup(
            id = session.allocateItemId(),
            kind = ItemSpawner.randomKind(random),
            x = mob.x,
            y = mob.y,
        )
        session.itemPickups.add(item)
        return GameEvent.ItemDropped(item.id, item.kind, item.x, item.y)
    }
}
