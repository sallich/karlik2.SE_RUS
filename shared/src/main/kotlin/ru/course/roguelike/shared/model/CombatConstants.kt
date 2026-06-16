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
    /** HP с игрока за одно мили-попадание (дробная часть копится на сервере). */
    const val MELEE_MOB_PLAYER_DAMAGE = 0.85f
    const val MELEE_MOB_DAMAGE = 1
    const val MELEE_ATTACK_RANGE = 0.85f
    const val MELEE_MOVE_SPEED = 2.2f
    const val MELEE_ATTACK_COOLDOWN_MS = 1200

    const val RANGED_MOB_HP = 10
    /** HP с игрока за попадание снаряда моба. */
    const val RANGED_MOB_PLAYER_DAMAGE = 0.85f
    const val RANGED_MOB_DAMAGE = 1
    const val RANGED_ATTACK_RANGE = 9f
    const val RANGED_MOVE_SPEED = 2.0f
    const val RANGED_ATTACK_COOLDOWN_MS = 1800
    const val RANGED_MIN_DISTANCE = 3.5f

    /** Высота летающих мобов над полом (заметно выше колонн). */
    const val FLYING_MOB_Z = 0.75f

    /** Радиус 3D-хитбокса моба по вертикали (половина высоты тела). */
    const val MOB_HIT_HALF_HEIGHT = 0.2f

    /** Минимальная дистанция между центрами мобов при движении. */
    const val MOB_SEPARATION_DISTANCE = 0.9f

    const val PROJECTILE_SPEED = 5.5f
}
