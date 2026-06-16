package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyActions
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.dsl.PolicyRule
import ru.course.roguelike.policy.dsl.StrategyArchetypes
import ru.course.roguelike.policy.dsl.PolicyStrategyCatalog
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.loop.ReplanReason
import ru.course.roguelike.policy.observation.FailureSignal
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.GridPos

object PolicyPromptBuilder {
    private val DSL_REFERENCE = """
        AgentPolicy JSON v4 (full) OR delta patch.

        THE KEY DECISION is `objective` — a single committed sub-goal YOU choose. The agent commits to it
        for `commitSteps` (5..60) and only asks you again when it is reached/invalid/expired. This is how
        YOU drive the playthrough; coded planners only execute your goal; reflex rules handle combat/reload/door-E.

        objective: {"kind":"enter_door|reach_key|reach_exit|explore|clear_room","target":"x,y","commitSteps":20}
          - target MUST be a concrete cell copied from the brief:
              enter_door  → a coord from knownDoors=(x,y,kind)
              reach_exit  → knownExitGate=(x,y)
              explore     → a coord from frontierTargets=(x,y)
              reach_key   → a visible key coord (items/keys in brief)
          - Pick ONE door/frontier and COMMIT. Do not flip between two leads — that causes the in/out
            corridor loop. Re-target only after the agent reports the goal done.

        Full policy: {"version":4,"phase":"your_label","objective":{...},"params":{"exploreMode":"frontier|unvisited|door_bias","unstuckMode":"retreat|door|frontier","doorAggression":"normal|patient","combatStyle":"plant|kite|chase","keyPriority":"keys_first|doors_first","riskLevel":"cautious|balanced|aggressive"},"rules":[...],"notes":"one sentence why"}
        Delta replan: {"version":4,"objective":{...},"patch":[{"whenClause":"stuck","action":"unstuck","enabled":true}],"params":{"combatStyle":"chase"},"notes":"why you changed strategy"}

        YOU control strategy via:
        - objective (where to go next)
        - phase (named plan label — show your intent)
        - notes (REQUIRED: one short sentence explaining your decision for the human observer)
        - params (how planners behave for many steps)
        - patch rules: enable/disable conditions, change action (e.g. combat_in_room→combat_kite)

        Strategy examples (inspiration — invent your own or mix params freely):
        ${StrategyArchetypes.promptExamples()}

        params.combatStyle (honored every combat step):
          plant — stand and shoot (default vs boss/ranged)
          kite  — backpedal while firing
          chase — push forward while firing

        params.keyPriority: keys_first (floor keys before key-doors) | doors_first
        params.riskLevel: cautious (wait longer before combat replan) | balanced | aggressive (replan sooner)

        patch examples:
          {"whenClause":"has_visible_item","action":"navigate_item","enabled":true}
          {"whenClause":"combat_in_room","action":"combat","enabled":true}
          {"whenClause":"explore","action":"explore","enabled":false}  // during boss focus

        Conditions (reactive guards + fallback; first match wins):
          needs_reload, combat_kite, combat_in_room, needs_room_exit
          at_door_ready, at_door_need_enter, can_interact
          inventory_full, needs_weapon, has_visible_item
          has_visible_key, has_all_keys, needs_keys
          corner_trapped, stuck, frontier_available, has_unvisited_exit, explore
          phase_is_<name> — matches policy.phase

        Actions: enter_door, approach_door, interact, exit_room, navigate_*, equip_weapon, manage_inventory, combat, unstuck, explore_unvisited

        Fair-play: you only see local map + known doors + frontier targets in the brief. No global map. Never invent coords.
        LAVA tiles appear as L in localMap — walkable but ~20 HP/s damage; never pick lava cells as objective targets.

        OUTPUT FORMAT (strict):
        - Reply with ONE raw JSON object only — no markdown, no ```json fences, no prose before/after.
        - `objective` is MANDATORY on initial policy and on replans that change strategy.
        - Minimal valid example:
          {"version":4,"phase":"EXPLORATION","objective":{"kind":"explore","target":"7,3","commitSteps":20},"params":{"exploreMode":"frontier","unstuckMode":"retreat","doorAggression":"normal","combatStyle":"kite","keyPriority":"doors_first","riskLevel":"balanced"},"rules":[],"notes":"Commit to frontier 7,3 first."}
    """.trimIndent()

    fun systemPrompt(): String = """
        You are a roguelike policy architect — the strategic brain of the agent. You make ALL navigation
        and macro-tactic decisions by committing one `objective` at a time and tuning params/rules.
        Coded planners are only "hands" that pathfind and sync; reflex guards handle reload/combat/door-E.
        Your job: complete the level (mob rooms → keys → exit gate) with YOUR chosen strategy.

        ${PolicyStrategyCatalog.promptSection}

        $DSL_REFERENCE
    """.trimIndent()

    fun replanUserMessage(
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
        reason: ReplanReason,
    ): String {
        if (reason == ReplanReason.LOOP_ESCAPE) {
            return loopEscapeUserMessage(snapshot, context, current)
        }
        val trail = context.snapshotTrail.takeLast(4).joinToString("\n") { e ->
            "  step=${e.step} pos=${e.pos} keys=${e.keys}"
        }
        val ctx = PolicySnapshotBrief.replanContext(reason.name, snapshot, context)
        return """
            Replan triggered.
            Context: $ctx
            trail=${context.visitedTrailSummary(6)}

            Recent snapshots:
            $trail

            ${antiRepeatSection(context)}

            Current phase=${current.phase ?: "none"} params=${current.params}
            Current objective=${current.objective?.let { "${it.kind} target=${it.target ?: "-"}" } ?: "none"}

            Decide the next strategy yourself from the facts above. Return delta JSON:
            {"version":4,"objective":{...},"patch":[...],"params":{...},"notes":"why you changed strategy"}.
            Set a fresh `objective` with concrete `target` from the brief. Pick your own `commitSteps` (5..60)
            for how long the agent should follow this plan before asking you again.
            ALWAYS set `notes` — one sentence for the human observer.
        """.trimIndent()
    }

    fun initialUserMessage(snapshot: GameSnapshot, context: PolicyContext): String {
        val brief = PolicySnapshotBrief.situation(snapshot, context)
        return """
            New labyrinth seed=${snapshot.seed}, runNonce=${context.runNonce}, llmSampleSeed=${context.llmSampleSeed}.
            $brief

            YOU decide the full strategy for this run — phase name, all params, and one committed objective
            with a concrete target from the brief (knownDoors, frontierTargets, exit gate, visible keys).
            Different runNonce / sampling should produce genuinely different plans: vary combat style, key
            priority, explore mode, door aggression, and which door or frontier you commit to first.
            Do not default to the same params every time.

            Output AgentPolicy JSON v4 with objective (required `target`) + params + `commitSteps` + notes.
            You choose commitSteps (5..60): how many steps to execute this plan before the next replan.
            Enable at_door_* rules. Keep needs_room_exit after combat.

            CRITICAL: include `"objective":{"kind":"...","target":"x,y","commitSteps":N}` — runs abort without it.
            Copy target from knownDoors or frontierTargets in the brief above. Raw JSON only, no markdown fences.
        """.trimIndent()
    }

    /** Follow-up user turn after the model returned invalid or incomplete JSON. */
    fun jsonCorrectionMessage(failure: PolicyLlmParseFailure): String {
        val preview = failure.rawSnippet.lines().take(8).joinToString("\n").take(400)
        val kindHint = when (failure.kind) {
            PolicyLlmParseFailure.Kind.EMPTY ->
                "Your previous reply was empty."
            PolicyLlmParseFailure.Kind.UNPARSEABLE ->
                "Your previous reply was not valid JSON."
            PolicyLlmParseFailure.Kind.MISSING_OBJECTIVE ->
                "JSON parsed but `objective` is missing — it is mandatory."
            PolicyLlmParseFailure.Kind.INVALID_OBJECTIVE ->
                "JSON parsed but `objective` failed validation."
        }
        return """
            CORRECTION REQUIRED — $kindHint
            Problem: ${failure.detail}

            Fragment of your previous reply:
            $preview

            Fix and reply with ONE raw JSON object (no markdown fences, no explanation text).
            REQUIRED: `"objective":{"kind":"explore|enter_door|reach_key|reach_exit|clear_room","target":"x,y","commitSteps":20}`
            where target is copied from knownDoors or frontierTargets in the original brief.
            Include version=4, params, notes (one sentence), and rules or empty rules array.
        """.trimIndent()
    }

    private fun antiRepeatSection(context: PolicyContext): String {
        if (context.macroDecisions.isEmpty()) return ""
        val prior = context.priorObjectiveTargets()
        val journal = context.macroDecisions.takeLast(4).joinToString("\n") { "  ${it.trackerLine()}" }
        return """
            Prior macro decisions this run:
            $journal
            Do NOT repeat failed objective targets: ${prior.joinToString()}.
            Pick a DIFFERENT door/frontier/exit from the brief than before unless you have a clear reason not to.
        """.trimIndent()
    }

    fun sanitize(raw: AgentPolicy): AgentPolicy {
        val knownConditions = PolicyMerger.CANONICAL_ORDER.toSet()
        val knownActions = setOf(
            PolicyActions.COMBAT,
            PolicyActions.COMBAT_KITE,
            PolicyActions.LEAVE_ROOM,
            PolicyActions.EXIT_ROOM,
            PolicyActions.RELOAD,
            PolicyActions.INTERACT,
            PolicyActions.ENTER_DOOR,
            PolicyActions.APPROACH_DOOR,
            PolicyActions.NAVIGATE_KEY,
            PolicyActions.NAVIGATE_DOOR,
            PolicyActions.NAVIGATE_EXIT,
            PolicyActions.NAVIGATE_ITEM,
            PolicyActions.EQUIP_WEAPON,
            PolicyActions.MANAGE_INVENTORY,
            PolicyActions.UNSTUCK,
            PolicyActions.EXPLORE,
            PolicyActions.EXPLORE_UNVISITED,
        )
        val cleaned = raw.rules.mapNotNull { rule ->
            val whenClause = rule.whenClause.lowercase()
            val action = rule.action.lowercase()
            if (whenClause !in knownConditions || action !in knownActions) return@mapNotNull null
            rule.copy(whenClause = whenClause, action = action)
        }
        return raw.copy(
            objective = sanitizeObjective(raw.objective),
            rules = cleaned.ifEmpty { DefaultPolicies.standardRules() },
        )
    }

    fun sanitizePatch(raw: PolicyPatchRequest): PolicyPatchRequest {
        val knownConditions = PolicyMerger.CANONICAL_ORDER.toSet()
        val cleaned = raw.patch.mapNotNull { rule ->
            val whenClause = rule.whenClause.lowercase()
            if (whenClause !in knownConditions) return@mapNotNull null
            rule.copy(whenClause = whenClause, action = rule.action.lowercase())
        }
        return raw.copy(patch = cleaned, objective = sanitizeObjective(raw.objective))
    }

    /**
     * Structural validation of an LLM objective: known [kind], parseable "x,y" [target] (else dropped),
     * and a clamped commit window. Whether the target cell is actually reachable is checked at
     * interpret-time against fair-play knowledge.
     */
    fun sanitizeObjective(objective: PolicyObjective?): PolicyObjective? {
        val obj = objective ?: return null
        val kind = obj.kind.lowercase()
        if (kind !in ObjectiveKinds.ALL) return null
        val target = obj.target?.let { parseCell(it) }?.let { "${it.x},${it.y}" }
        if (kind in ObjectiveKinds.TARGETED && target == null) return null
        val commit = obj.commitSteps.coerceIn(
            ObjectiveKinds.MIN_COMMIT_STEPS,
            ObjectiveKinds.MAX_COMMIT_STEPS,
        )
        return PolicyObjective(kind = kind, target = target, commitSteps = commit)
    }

    private fun loopEscapeUserMessage(
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
    ): String = """
        URGENT loop escape — the agent is repeating the same steps without progress (ping-pong / same guard firing).
        ${PolicySnapshotBrief.situation(snapshot, context)}

        Action trace (newest last):
        ${context.actionTraceSummary(10)}

        Position trail: ${context.visitedTrailSummary(8)}
        Known doors: ${context.knowledge.formatKnownDoors(6)}
        Frontier: ${context.knowledge.formatFrontierTargets(snapshot, 4)}

        Current phase=${current.phase ?: "none"} params=${current.params}
        Current objective=${current.objective?.let { "${it.kind} target=${it.target ?: "-"}" } ?: "none"}

        ${antiRepeatSection(context)}

        You must break the loop with a fresh strategy of your own:
        - New `objective` with a concrete `target` DIFFERENT from the loop (another door, frontier, exit)
        - Adjust params and patch rules as YOU see fit
        - Explain in `notes` why this plan should escape the loop

        Return delta JSON v4: {"version":4,"objective":{...},"patch":[...],"params":{...},"notes":"..."}.
    """.trimIndent()

    private fun parseCell(key: String): GridPos? {
        val parts = key.split(",")
        if (parts.size != 2) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        return GridPos(x, y)
    }
}
