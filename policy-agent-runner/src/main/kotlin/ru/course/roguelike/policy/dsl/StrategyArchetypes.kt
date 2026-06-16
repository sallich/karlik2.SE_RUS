package ru.course.roguelike.policy.dsl

/**
 * Named strategy presets shown to the LLM as inspiration in the system prompt.
 * The model chooses freely — these are not picked or enforced by code at runtime.
 */
object StrategyArchetypes {
    data class Archetype(
        val phase: String,
        val exploreMode: String,
        val combatStyle: String,
        val keyPriority: String,
        val riskLevel: String,
        val unstuckMode: String,
        val doorAggression: String,
        val hint: String,
    )

    val ALL: List<Archetype> = listOf(
        Archetype(
            "scout", PolicyParams.EXPLORE_UNVISITED, PolicyParams.COMBAT_PLANT,
            PolicyParams.KEYS_FIRST, PolicyParams.RISK_BALANCED,
            PolicyParams.UNSTUCK_DOOR, PolicyParams.DOOR_PATIENT,
            "Map wide before committing to doors",
        ),
        Archetype(
            "door_hunter", PolicyParams.EXPLORE_DOOR_BIAS, PolicyParams.COMBAT_CHASE,
            PolicyParams.DOORS_FIRST, PolicyParams.RISK_AGGRESSIVE,
            PolicyParams.UNSTUCK_DOOR, PolicyParams.DOOR_NORMAL,
            "Push known mob doors early",
        ),
        Archetype(
            "key_rush", PolicyParams.EXPLORE_FRONTIER, PolicyParams.COMBAT_KITE,
            PolicyParams.KEYS_FIRST, PolicyParams.RISK_BALANCED,
            PolicyParams.UNSTUCK_FRONTIER, PolicyParams.DOOR_NORMAL,
            "Floor keys before key-doors",
        ),
        Archetype(
            "cautious", PolicyParams.EXPLORE_FRONTIER, PolicyParams.COMBAT_PLANT,
            PolicyParams.KEYS_FIRST, PolicyParams.RISK_CAUTIOUS,
            PolicyParams.UNSTUCK_RETREAT, PolicyParams.DOOR_PATIENT,
            "Patient exploration and safe combat",
        ),
        Archetype(
            "blitzer", PolicyParams.EXPLORE_DOOR_BIAS, PolicyParams.COMBAT_CHASE,
            PolicyParams.DOORS_FIRST, PolicyParams.RISK_AGGRESSIVE,
            PolicyParams.UNSTUCK_FRONTIER, PolicyParams.DOOR_NORMAL,
            "Fast clears, aggressive replans",
        ),
        Archetype(
            "flanker", PolicyParams.EXPLORE_UNVISITED, PolicyParams.COMBAT_KITE,
            PolicyParams.KEYS_FIRST, PolicyParams.RISK_AGGRESSIVE,
            PolicyParams.UNSTUCK_FRONTIER, PolicyParams.DOOR_NORMAL,
            "Kite mobs, wide pathing",
        ),
    )

    fun promptExamples(): String = ALL.joinToString("\n") { archetype ->
        "  ${archetype.phase} — ${archetype.hint} " +
            "(explore=${archetype.exploreMode}, combat=${archetype.combatStyle}, " +
            "keys=${archetype.keyPriority}, risk=${archetype.riskLevel})"
    }
}
