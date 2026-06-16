package ru.course.roguelike.policy.llm

import kotlinx.serialization.Serializable
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.policy.dsl.PolicyRule

@Serializable
data class PolicyPatchRequest(
    val version: Int = 4,
    val phase: String? = null,
    val objective: PolicyObjective? = null,
    val params: PolicyParams? = null,
    val patch: List<PolicyRule> = emptyList(),
    val notes: String? = null,
)

object PolicyMerger {
    val CANONICAL_ORDER: List<String> = listOf(
        ru.course.roguelike.policy.dsl.PolicyConditions.COMBAT_KITE,
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_RELOAD,
        ru.course.roguelike.policy.dsl.PolicyConditions.COMBAT_IN_ROOM,
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_ROOM_EXIT,
        ru.course.roguelike.policy.dsl.PolicyConditions.AT_DOOR_READY,
        ru.course.roguelike.policy.dsl.PolicyConditions.AT_DOOR_NEED_ENTER,
        ru.course.roguelike.policy.dsl.PolicyConditions.CAN_INTERACT,
        ru.course.roguelike.policy.dsl.PolicyConditions.NEAR_VISIBLE_KEY,
        ru.course.roguelike.policy.dsl.PolicyConditions.INVENTORY_FULL,
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_WEAPON,
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_VISIBLE_ITEM,
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_VISIBLE_KEY,
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_ALL_KEYS,
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_KEYS,
        ru.course.roguelike.policy.dsl.PolicyConditions.CORNER_TRAPPED,
        ru.course.roguelike.policy.dsl.PolicyConditions.STUCK,
        ru.course.roguelike.policy.dsl.PolicyConditions.FRONTIER_AVAILABLE,
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_UNVISITED_EXIT,
        ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE,
    )

    private val ALLOWED_ACTIONS: Map<String, Set<String>> = mapOf(
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_RELOAD to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.RELOAD,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_ROOM_EXIT to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.EXIT_ROOM,
            ru.course.roguelike.policy.dsl.PolicyActions.LEAVE_ROOM,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.COMBAT_KITE to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.COMBAT_KITE,
            ru.course.roguelike.policy.dsl.PolicyActions.COMBAT,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.COMBAT_IN_ROOM to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.COMBAT,
            ru.course.roguelike.policy.dsl.PolicyActions.COMBAT_KITE,
            ru.course.roguelike.policy.dsl.PolicyActions.RELOAD,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.AT_DOOR_READY to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.ENTER_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.INTERACT,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.AT_DOOR_NEED_ENTER to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.APPROACH_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.UNSTUCK,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.CAN_INTERACT to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.INTERACT,
            ru.course.roguelike.policy.dsl.PolicyActions.ENTER_DOOR,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.NEAR_VISIBLE_KEY to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_KEY,
            ru.course.roguelike.policy.dsl.PolicyActions.INTERACT,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.INVENTORY_FULL to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.MANAGE_INVENTORY,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_WEAPON to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.EQUIP_WEAPON,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_ITEM,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_VISIBLE_ITEM to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_ITEM,
            ru.course.roguelike.policy.dsl.PolicyActions.INTERACT,
            ru.course.roguelike.policy.dsl.PolicyActions.MANAGE_INVENTORY,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_VISIBLE_KEY to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_KEY,
            ru.course.roguelike.policy.dsl.PolicyActions.INTERACT,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_ALL_KEYS to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_EXIT,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.NEEDS_KEYS to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.APPROACH_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_KEY,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE_UNVISITED,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.CORNER_TRAPPED to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.UNSTUCK,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.STUCK to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.UNSTUCK,
            ru.course.roguelike.policy.dsl.PolicyActions.EXIT_ROOM,
            ru.course.roguelike.policy.dsl.PolicyActions.APPROACH_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE_UNVISITED,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.FRONTIER_AVAILABLE to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE_UNVISITED,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.HAS_UNVISITED_EXIT to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE_UNVISITED,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_DOOR,
        ),
        ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE to setOf(
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE,
            ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE_UNVISITED,
            ru.course.roguelike.policy.dsl.PolicyActions.NAVIGATE_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.APPROACH_DOOR,
            ru.course.roguelike.policy.dsl.PolicyActions.UNSTUCK,
        ),
    )

    fun mergeWithBaseline(llm: AgentPolicy): AgentPolicy {
        val baseline = ru.course.roguelike.policy.dsl.DefaultPolicies.standardRules().associateBy { it.whenClause.lowercase() }
        val llmByClause = llm.rules.associateBy { it.whenClause.lowercase() }
        val merged = CANONICAL_ORDER.mapNotNull { clause ->
            val llmRule = llmByClause[clause]
            val base = baseline[clause] ?: return@mapNotNull null
            val source = llmRule ?: base
            val enabled = llmRule?.enabled ?: base.enabled
            val action = resolveAction(clause, source.action.lowercase(), base.action.lowercase())
            source.copy(whenClause = clause, action = action, enabled = enabled)
        }
        return ensureExploreLast(
            AgentPolicy(
                version = 4,
                phase = llm.phase,
                objective = llm.objective,
                params = sanitizeParams(llm.params),
                rules = merged.ifEmpty { ru.course.roguelike.policy.dsl.DefaultPolicies.standardRules() },
                notes = llm.notes,
            ),
        )
    }

    fun applyPatch(current: AgentPolicy, patch: PolicyPatchRequest): AgentPolicy {
        val byClause = current.rules.associateBy { it.whenClause.lowercase() }.toMutableMap()
        for (rule in patch.patch) {
            val clause = rule.whenClause.lowercase()
            if (clause !in CANONICAL_ORDER) continue
            val base = byClause[clause] ?: continue
            val action = resolveAction(clause, rule.action.lowercase(), base.action.lowercase())
            byClause[clause] = base.copy(
                enabled = rule.enabled,
                action = action,
                target = rule.target ?: base.target,
            )
        }
        val merged = mergeWithBaseline(
            current.copy(
                phase = patch.phase ?: current.phase,
                objective = patch.objective ?: current.objective,
                params = patch.params ?: current.params,
                rules = byClause.values.toList(),
                notes = patch.notes ?: current.notes,
            ),
        )
        return merged
    }

    private fun sanitizeParams(params: PolicyParams): PolicyParams {
        val explore = params.exploreMode.takeIf {
            it in setOf(
                PolicyParams.EXPLORE_UNVISITED,
                PolicyParams.EXPLORE_FRONTIER,
                PolicyParams.EXPLORE_DOOR_BIAS,
            )
        } ?: PolicyParams.EXPLORE_UNVISITED
        val unstuck = params.unstuckMode.takeIf {
            it in setOf(
                PolicyParams.UNSTUCK_DOOR,
                PolicyParams.UNSTUCK_RETREAT,
                PolicyParams.UNSTUCK_FRONTIER,
            )
        } ?: PolicyParams.UNSTUCK_DOOR
        val door = params.doorAggression.takeIf {
            it in setOf(PolicyParams.DOOR_NORMAL, PolicyParams.DOOR_PATIENT)
        } ?: PolicyParams.DOOR_NORMAL
        val combat = params.combatStyle.takeIf {
            it in setOf(PolicyParams.COMBAT_PLANT, PolicyParams.COMBAT_KITE, PolicyParams.COMBAT_CHASE)
        } ?: PolicyParams.COMBAT_PLANT
        val keys = params.keyPriority.takeIf {
            it in setOf(PolicyParams.KEYS_FIRST, PolicyParams.DOORS_FIRST)
        } ?: PolicyParams.KEYS_FIRST
        val risk = params.riskLevel.takeIf {
            it in setOf(PolicyParams.RISK_CAUTIOUS, PolicyParams.RISK_BALANCED, PolicyParams.RISK_AGGRESSIVE)
        } ?: PolicyParams.RISK_BALANCED
        return PolicyParams(
            exploreMode = explore,
            unstuckMode = unstuck,
            doorAggression = door,
            combatStyle = combat,
            keyPriority = keys,
            riskLevel = risk,
        )
    }

    private fun resolveAction(clause: String, llmAction: String, baseAction: String): String {
        val allowed = ALLOWED_ACTIONS[clause] ?: return baseAction
        return if (llmAction in allowed) llmAction else baseAction
    }

    private fun ensureExploreLast(policy: AgentPolicy): AgentPolicy {
        val explore = policy.rules.filter { it.whenClause == ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE }
        val rest = policy.rules.filter { it.whenClause != ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE }
        val rules = if (explore.isEmpty()) {
            rest + PolicyRule(ru.course.roguelike.policy.dsl.PolicyConditions.EXPLORE, ru.course.roguelike.policy.dsl.PolicyActions.EXPLORE)
        } else {
            rest + explore
        }
        return policy.copy(rules = rules)
    }

    /**
     * Strip code-injected navigation fallbacks (e.g. from a failed loop-escape patch) so the next LLM
     * replan starts from baseline reflex defaults, not polluted rule toggles.
     */
    fun cleanNavigationFallbacks(policy: AgentPolicy): AgentPolicy {
        val baseline = ru.course.roguelike.policy.dsl.DefaultPolicies.standardRules()
            .associateBy { it.whenClause.lowercase() }
        val resetClauses = setOf(
            ru.course.roguelike.policy.dsl.PolicyConditions.STUCK,
            ru.course.roguelike.policy.dsl.PolicyConditions.CORNER_TRAPPED,
            ru.course.roguelike.policy.dsl.PolicyConditions.FRONTIER_AVAILABLE,
            ru.course.roguelike.policy.dsl.PolicyConditions.HAS_UNVISITED_EXIT,
        )
        val byClause = policy.rules.associateBy { it.whenClause.lowercase() }.toMutableMap()
        for (clause in resetClauses) {
            baseline[clause]?.let { byClause[clause] = it }
        }
        return mergeWithBaseline(
            policy.copy(rules = byClause.values.toList()),
        )
    }
}
