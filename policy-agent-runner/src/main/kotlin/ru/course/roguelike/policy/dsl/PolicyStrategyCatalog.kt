package ru.course.roguelike.policy.dsl



/**

 * Maps DSL actions to coded planners. The LLM picks/composes rules — it does not emit algorithms.

 */

object PolicyStrategyCatalog {

    val promptSection: String = """

        Macro (LLM JSON v4) vs micro (code planners each step):



        Game flow: corridor → approach_door → enter_door (E) → combat → exit_room → keys → exit gate.

        Fair-play: planners and LLM only use visited cells + local visibility (radius 2).



        YOU are the brain: choose objective, params, phase, and rule patches at each replan.

        Planners are hands — pathfinding, FPS sync, interact. Reflex guards handle reload/combat/door-E.



        Params:

          exploreMode: unvisited | frontier | door_bias

          unstuckMode: door | retreat | frontier

          doorAggression: normal | patient

          combatStyle: plant | kite | chase

          keyPriority: keys_first | doors_first

          riskLevel: cautious | balanced | aggressive



        LLM decisions are logged in macroJournal (tracker) — use phase + notes so runs differ visibly.



        Planners (policy-agent-runner):

          approach_door / enter_door → PolicyDoorPlanner (FPS, known doors only)

          exit_room → PolicyRoomExitPlanner (FPS BFS)

          navigate_* → PolicyKeyHuntPlanner (known goals, FPS path)

          explore → PolicyExploreHelper (frontier / unvisited)

          unstuck → PolicyUnstuckPlanner (corner retreat, frontier)

          combat / reload → AgentCombatHelper



        Replan: return delta patch JSON with a fresh objective. failureSignals are facts, not orders.

        PolicyDeterministicPatch runs ONLY when Ollama is unavailable — keeps your strategy, toggles reflex rules only.

    """.trimIndent()

}

