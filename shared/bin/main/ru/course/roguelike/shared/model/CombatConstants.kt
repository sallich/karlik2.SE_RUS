package ru.course.roguelike.shared.model

/** Константы боя для прототипа мобов. */
object CombatConstants {
    const val MOB_RADIUS = 0.35f
    const val PROJECTILE_RADIUS = 0.12f

    const val PLAYER_ATTACK_DAMAGE = 25
    const val PLAYER_ATTACK_COOLDOWN_MS = 450
    const val PLAYER_PROJECTILE_SPEED = 7f

    /** Боезапас героя: стартовый, максимальный и сколько даёт подобранный ящик с патронами (issue #9). */
    const val PLAYER_STARTING_AMMO = 50
    const val PLAYER_MAX_AMMO = 99

    const val MELEE_MOB_HP = 10
    const val MELEE_MOB_DAMAGE = 1
    const val MELEE_ATTACK_RANGE = 0.85f
    const val MELEE_MOVE_SPEED = 2.2f
    const val MELEE_ATTACK_COOLDOWN_MS = 900

    const val RANGED_MOB_HP = 10
    const val RANGED_MOB_DAMAGE = 1
    const val RANGED_ATTACK_RANGE = 9f
    const val RANGED_MOVE_SPEED = 1.4f
    const val RANGED_ATTACK_COOLDOWN_MS = 1400
    const val RANGED_MIN_DISTANCE = 3.5f

    const val PROJECTILE_SPEED = 5.5f
}
