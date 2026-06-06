package ru.course.roguelike.shared.model

object ExperienceProgression {
    const val STARTING_LEVEL = 1

    const val BASE_MAX_HP = 100
    const val HP_PER_LEVEL = 20

    const val BASE_ATTACK_DAMAGE = CombatConstants.PLAYER_ATTACK_DAMAGE
    const val DAMAGE_PER_LEVEL = 5

    const val MELEE_MOB_XP = 25
    const val RANGED_MOB_XP = 35
    const val LLM_GUARD_MOB_XP = 50
    const val LOCATION_COMPLETION_XP = 100

    fun xpRequiredForNextLevel(level: Int): Int = 100 + (level - 1) * 50

    fun totalXpForLevel(level: Int): Int {
        if (level <= STARTING_LEVEL) return 0
        return (STARTING_LEVEL until level).sumOf { xpRequiredForNextLevel(it) }
    }

    /** Текущий уровень по накопленному опыту. */
    fun levelFromTotalXp(totalXp: Int): Int {
        var level = STARTING_LEVEL
        var remaining = totalXp
        while (remaining >= xpRequiredForNextLevel(level)) {
            remaining -= xpRequiredForNextLevel(level)
            level++
        }
        return level
    }

    fun xpProgressInLevel(totalXp: Int): Pair<Int, Int> {
        val level = levelFromTotalXp(totalXp)
        val xpInLevel = totalXp - totalXpForLevel(level)
        return xpInLevel to xpRequiredForNextLevel(level)
    }

    fun maxHpForLevel(level: Int): Int = BASE_MAX_HP + (level - STARTING_LEVEL) * HP_PER_LEVEL

    fun attackDamageForLevel(level: Int): Int = BASE_ATTACK_DAMAGE + (level - STARTING_LEVEL) * DAMAGE_PER_LEVEL

    fun mobKillXp(kind: MobKind): Int = when (kind) {
        MobKind.MELEE -> MELEE_MOB_XP
        MobKind.RANGED -> RANGED_MOB_XP
        MobKind.LLM_GUARD -> LLM_GUARD_MOB_XP
    }
}
