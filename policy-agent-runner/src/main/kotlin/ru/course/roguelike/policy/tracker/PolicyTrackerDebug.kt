package ru.course.roguelike.policy.tracker



import kotlin.math.cos

import kotlin.math.floor

import kotlin.math.sin

import ru.course.roguelike.agent.map.MapCellRenderer

import ru.course.roguelike.agent.tracker.TrackerHighlight

import ru.course.roguelike.policy.knowledge.PartialTileMap

import ru.course.roguelike.policy.loop.PolicyContext

import ru.course.roguelike.policy.observation.PolicyObservation

import ru.course.roguelike.policy.planner.PolicyRoomExitPlanner

import ru.course.roguelike.shared.dto.GameSnapshot

import ru.course.roguelike.shared.model.GridPos



object PolicyTrackerDebug {

    fun build(snapshot: GameSnapshot, context: PolicyContext, condition: String): Pair<List<String>, List<TrackerHighlight>> {

        val px = floor(snapshot.player.pose.x).toInt()

        val py = floor(snapshot.player.pose.y).toInt()

        val yaw = snapshot.player.pose.yaw

        val aheadX = floor(snapshot.player.pose.x + cos(yaw.toDouble()) * 0.6).toInt()

        val aheadY = floor(snapshot.player.pose.y + sin(yaw.toDouble()) * 0.6).toInt()



        val obs = context.lastObservation ?: PolicyObservation.observe(snapshot, context)

        val region = context.frozenRegionCellKeys()

        val navGoals = if (region != null) {

        val map = ru.course.roguelike.shared.engine.TileMap.fromFlat(
            snapshot.width,
            snapshot.height,
            snapshot.tiles,
        )

            PolicyRoomExitPlanner.findExitGoals(

                map,

                region.map { key ->

                    val p = key.split(",")

                    GridPos(p[0].toInt(), p[1].toInt())

                }.toSet(),

                GridPos(px, py),

                snapshot,

            ).take(3).map { "${it.x},${it.y}" }

        } else {

            emptyList()

        }

        val frontier = context.knowledge.frontierCells(snapshot).take(3).map { "${it.x},${it.y}" }

        val lines = buildList {

            add("policy: $condition")

            add("macro: ${context.llmProvider} · ${context.lastPolicySource}")
            if (context.lastPolicySource.contains("llm-unavailable") ||
                context.lastPolicySource.contains("initial-llm-unavailable")
            ) {
                add("WARNING: macro brain did NOT respond — check GET /health field llm.hint")
            }

            add("runNonce=${context.runNonce} llmSampleSeed=${context.llmSampleSeed}")

            context.activeObjective?.let { obj ->

                add("objective: ${obj.kind} target=${obj.target ?: "-"} commit=${obj.commitSteps}")

            }
            context.currentPolicy?.params?.let { p ->
                add("params: combat=${p.combatStyle} keys=${p.keyPriority} explore=${p.exploreMode} risk=${p.riskLevel}")
            }
            context.currentPolicy?.phase?.let { add("phase: $it") }
            context.currentPolicy?.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { add("llm: $it") }
            context.macroDecisions.takeLast(3).forEach { add(it.trackerLine()) }
            if (context.combatMobHpStallSteps > 0) {
                add("combatStall=${context.combatMobHpStallSteps}")
            }

            add("situation: ${obs.situation.name}")

            add("hp: ${snapshot.player.hp}/${snapshot.player.maxHp}")

            if (obs.failureSignals.isNotEmpty()) {

                add("signals: ${obs.failureSignals.joinToString { it.name }}")

            }

            val lavaKnown = context.knowledge.formatKnownLava(5)
            if (lavaKnown != "none known") add("knownLava: $lavaKnown")
            MapCellRenderer.hazardsNear(snapshot, px, py).takeIf { it.isNotEmpty() }?.let { add(it) }

            add("seekExit=${context.seekRoomExit} mobPending=${context.mobRoomExitPending}")

            add("knownCells=${context.knowledge.knownCells.size} visited=${context.visitedCells.size}")

            add("knownDoors: ${context.knowledge.formatKnownDoors(4)}")

            if (frontier.isNotEmpty()) add("frontier: ${frontier.joinToString()}")

            add("under @ ($px,$py): ${MapCellRenderer.describeCell(snapshot, px, py)}")

            add("facing → ($aheadX,$aheadY): ${MapCellRenderer.describeCell(snapshot, aheadX, aheadY)}")

            MapCellRenderer.verticalFeaturesNear(snapshot, px, py).takeIf { it.isNotEmpty() }?.let {

                add("nearby: $it")

            }

            context.lastUnstuckTargetKey?.let { add("move target cell: $it") }

            context.frozenExitGoalCellKeys()?.take(5)?.let { goals ->

                add("exit goals: ${goals.joinToString()}")

            }

            if (navGoals.isNotEmpty()) {

                add("nav goals: ${navGoals.joinToString()}")

            }

        }



        val highlights = buildList {

            context.knowledge.knownCells.forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "known")) }

            }

            context.visitedCells.forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "visited")) }

            }

            context.frozenRegionCellKeys()?.forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "frozen")) }

            }

            context.frozenExitGoalCellKeys()?.forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "exit_goal")) }

            }

            navGoals.forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "nav_goal")) }

            }

            frontier.forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "frontier")) }

            }

            context.knowledge.knownLavaCellKeys().forEach { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "lava")) }

            }

            context.lastUnstuckTargetKey?.let { key ->

                parse(key)?.let { add(TrackerHighlight(it.x, it.y, "target")) }

            }

            add(TrackerHighlight(aheadX, aheadY, "aim"))

        }



        return lines to highlights

    }



    private fun parse(key: String): GridPos? {

        val parts = key.split(",")

        if (parts.size != 2) return null

        return GridPos(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)

    }

}

