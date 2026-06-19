package ru.course.roguelike.policy.dsl

import kotlinx.serialization.Serializable

/**
 * Declarative policy the LLM may generate or patch.
 * The interpreter maps each rule to existing planner primitives (no direct game-service access).
 */
@Serializable
data class AgentPolicy(
    val version: Int = 4,
    val phase: String? = null,
    val params: PolicyParams = PolicyParams(),
    /**
     * Committed navigation sub-goal the LLM picks each replan. Honored by the interpreter across the
     * whole replan window (planners just execute it), which replaces the per-step goal-flipping that
     * caused corridor/door ping-pong. Reactive guards (combat/reload/door-E/stuck) still override it.
     */
    val objective: PolicyObjective? = null,
    /** Evaluated top-to-bottom; first matching enabled rule wins. Used as reactive guards + fallback. */
    val rules: List<PolicyRule> = DefaultPolicies.standardRules(),
    val notes: String? = null,
)

/**
 * A single committed goal expressed in fair-play terms. [target] is a concrete "x,y" cell the LLM
 * picks from the brief (a known door / frontier anchor / exit gate / visible key) — never a global
 * map lookup. [commitSteps] bounds how long the interpreter stays committed before the LLM re-decides.
 */
@Serializable
data class PolicyObjective(
    val kind: String,
    val target: String? = null,
    val commitSteps: Int = ObjectiveKinds.DEFAULT_COMMIT_STEPS,
)

object ObjectiveKinds {
    const val ENTER_DOOR = "enter_door"
    const val REACH_KEY = "reach_key"
    const val REACH_EXIT = "reach_exit"
    const val EXPLORE = "explore"
    const val CLEAR_ROOM = "clear_room"

    const val DEFAULT_COMMIT_STEPS = 12
    const val MIN_COMMIT_STEPS = 5
    const val MAX_COMMIT_STEPS = 25

    val ALL: Set<String> = setOf(ENTER_DOOR, REACH_KEY, REACH_EXIT, EXPLORE, CLEAR_ROOM)

    /** Kinds whose [PolicyObjective.target] is required to be meaningful (else the goal degrades). */
    val TARGETED: Set<String> = setOf(ENTER_DOOR, REACH_KEY, REACH_EXIT, EXPLORE)
}

@Serializable
data class PolicyParams(
    val exploreMode: String = EXPLORE_FRONTIER,
    val unstuckMode: String = UNSTUCK_DOOR,
    val doorAggression: String = DOOR_NORMAL,
    /** LLM-tunable combat micro-style inside mob rooms (planners honor this each step). */
    val combatStyle: String = COMBAT_PLANT,
    /** Whether to path to floor keys before key-doors when both are visible. */
    val keyPriority: String = KEYS_FIRST,
    /** Risk appetite — affects combat-stall replan timing (LLM-tunable). */
    val riskLevel: String = RISK_BALANCED,
) {
    companion object {
        const val EXPLORE_UNVISITED = "unvisited"
        const val EXPLORE_FRONTIER = "frontier"
        const val EXPLORE_DOOR_BIAS = "door_bias"
        const val UNSTUCK_DOOR = "door"
        const val UNSTUCK_RETREAT = "retreat"
        const val UNSTUCK_FRONTIER = "frontier"
        const val DOOR_NORMAL = "normal"
        const val DOOR_PATIENT = "patient"
        const val COMBAT_PLANT = "plant"
        const val COMBAT_KITE = "kite"
        const val COMBAT_CHASE = "chase"
        const val KEYS_FIRST = "keys_first"
        const val DOORS_FIRST = "doors_first"
        const val RISK_CAUTIOUS = "cautious"
        const val RISK_BALANCED = "balanced"
        const val RISK_AGGRESSIVE = "aggressive"
    }
}

@Serializable
data class PolicyRule(
    val whenClause: String,
    val action: String,
    val target: String? = null,
    val enabled: Boolean = true,
)

object PolicyConditions {
    const val COMBAT_IN_ROOM = "combat_in_room"
    const val NEEDS_ROOM_EXIT = "needs_room_exit"
    const val COMBAT_KITE = "combat_kite"
    const val NEEDS_RELOAD = "needs_reload"
    const val AT_DOOR_READY = "at_door_ready"
    const val AT_DOOR_NEED_ENTER = "at_door_need_enter"
    const val CAN_INTERACT = "can_interact"
    const val INVENTORY_FULL = "inventory_full"
    const val NEEDS_WEAPON = "needs_weapon"
    const val HAS_VISIBLE_ITEM = "has_visible_item"
    const val HAS_VISIBLE_KEY = "has_visible_key"
    /** Visible key within pickup approach range — guard that overrides a committed objective. */
    const val NEAR_VISIBLE_KEY = "near_visible_key"
    const val NEEDS_KEYS = "needs_keys"
    const val HAS_ALL_KEYS = "has_all_keys"
    const val HAS_UNVISITED_EXIT = "has_unvisited_exit"
    const val STUCK = "stuck"
    const val CORNER_TRAPPED = "corner_trapped"
    const val FRONTIER_AVAILABLE = "frontier_available"
    const val EXPLORE = "explore"
}

object PolicyActions {
    const val COMBAT = "combat"
    const val COMBAT_KITE = "combat_kite"
    const val LEAVE_ROOM = "leave_room"
    const val EXIT_ROOM = "exit_room"
    const val RELOAD = "reload"
    const val INTERACT = "interact"
    const val ENTER_DOOR = "enter_door"
    const val APPROACH_DOOR = "approach_door"
    const val NAVIGATE_KEY = "navigate_key"
    const val NAVIGATE_DOOR = "navigate_door"
    const val NAVIGATE_EXIT = "navigate_exit"
    const val NAVIGATE_ITEM = "navigate_item"
    const val EQUIP_WEAPON = "equip_weapon"
    const val MANAGE_INVENTORY = "manage_inventory"
    const val UNSTUCK = "unstuck"
    const val EXPLORE = "explore"
    const val EXPLORE_UNVISITED = "explore_unvisited"
}

object PolicyTargets {
    const val NEAREST_KEY_DOOR = "nearest_key_door"
    const val NEAREST_DOOR = "nearest_door"
    const val NEAREST_KNOWN = "nearest_known"
    const val EXIT = "exit"
}

object DefaultPolicies {
    fun standard(): AgentPolicy = AgentPolicy(rules = standardRules())

    fun standardRules(): List<PolicyRule> = listOf(
        PolicyRule(PolicyConditions.COMBAT_KITE, PolicyActions.COMBAT_KITE),
        PolicyRule(PolicyConditions.NEEDS_RELOAD, PolicyActions.RELOAD),
        PolicyRule(PolicyConditions.COMBAT_IN_ROOM, PolicyActions.COMBAT),
        PolicyRule(PolicyConditions.NEEDS_ROOM_EXIT, PolicyActions.EXIT_ROOM),
        PolicyRule(PolicyConditions.AT_DOOR_READY, PolicyActions.ENTER_DOOR),
        PolicyRule(PolicyConditions.AT_DOOR_NEED_ENTER, PolicyActions.APPROACH_DOOR),
        PolicyRule(PolicyConditions.CAN_INTERACT, PolicyActions.INTERACT),
        PolicyRule(PolicyConditions.NEAR_VISIBLE_KEY, PolicyActions.NAVIGATE_KEY),
        PolicyRule(PolicyConditions.INVENTORY_FULL, PolicyActions.MANAGE_INVENTORY, enabled = false),
        PolicyRule(PolicyConditions.NEEDS_WEAPON, PolicyActions.EQUIP_WEAPON, enabled = false),
        PolicyRule(PolicyConditions.HAS_VISIBLE_ITEM, PolicyActions.NAVIGATE_ITEM, enabled = false),
        PolicyRule(PolicyConditions.HAS_VISIBLE_KEY, PolicyActions.NAVIGATE_KEY),
        PolicyRule(PolicyConditions.HAS_ALL_KEYS, PolicyActions.NAVIGATE_EXIT, PolicyTargets.EXIT),
        PolicyRule(PolicyConditions.NEEDS_KEYS, PolicyActions.NAVIGATE_DOOR, PolicyTargets.NEAREST_KEY_DOOR),
        PolicyRule(PolicyConditions.CORNER_TRAPPED, PolicyActions.UNSTUCK, enabled = false),
        PolicyRule(PolicyConditions.STUCK, PolicyActions.UNSTUCK),
        PolicyRule(PolicyConditions.FRONTIER_AVAILABLE, PolicyActions.EXPLORE_UNVISITED, enabled = false),
        PolicyRule(PolicyConditions.HAS_UNVISITED_EXIT, PolicyActions.EXPLORE_UNVISITED, enabled = false),
        PolicyRule(PolicyConditions.EXPLORE, PolicyActions.EXPLORE),
    )
}
