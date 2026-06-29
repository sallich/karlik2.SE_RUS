package ru.course.roguelike.agent.llm

import kotlin.math.floor
import kotlin.math.hypot
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.combat.AgentCombatHelper
import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.map.MapCellRenderer
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.InteractionConstants
import ru.course.roguelike.shared.protocol.GameActions

enum class AgentPromptStyle {
    COMPACT,
    EMERGENCY,
}

object AgentPromptBuilder {
    private const val GRID_ACTIONS =
        "move_north, move_south, move_east, move_west, turn_left, turn_right, interact, wait"

    private const val INTERACT_E_LINE =
        "interact = player key E (game_act action=interact). Opens room doors (D), picks keys, exits."

    private val DOOR_RULES = """
        DOORS — enter mob/key rooms to progress (D on map):
        1. Goal in EXPLORATION: reach a D tile and press E. Mobs on the level are INSIDE sealed rooms — you cannot fight until you enter.
        2. D is a WALL (ROOM_SEAL) — move_* CANNOT walk into D; you stay stuck if you try.
        3. Use the "All doors" list + compass line to navigate when D is off the mini-map.
        4. When D appears on map: walk to a FLOOR tile adjacent to D (not onto D).
        5. Face D: game_act turn_left or turn_right until yaw points at D.
        6. game_act action=interact (E) — you teleport into the room; roomTimer starts; then use game_sync attack.
        # alone is a plain wall — never interact on #. Only interact when D is visible or DOOR READY says so.
    """.trimIndent()

    private val COMBAT_RULES = """
        COMBAT — only AFTER entering a room (roomTimerMs set, phase may stay EXPLORATION briefly):
        - roomTimer=none → do NOT game_sync attack; corridor mobs are unreachable. Find D and interact first.
        - roomTimer active → game_sync attack=true with clientYaw AND clientPitch from prompt; reload if ammo=0.
        - R (ranged) and G (guard) mobs fly above the floor — clientPitch must aim UP (positive), not 0.
    """.trimIndent()

    private val WALL_NAVIGATION_RULES = """
        WALLS & CORRIDORS — you cannot walk through # or D:
        1. move_* only enters walkable floor (.) — check "Corridor exits" line each turn.
        2. If position unchanged after move_* — that direction is blocked. NEVER repeat it.
        3. Detour is OK: step into any OPEN exit, even away from the door, to slide along walls to a corridor.
        4. Think grid path: go around # corners until the door compass offset can decrease again.
        5. # = plain wall (no interact). D = door seal — stand on floor beside D, face D, then interact (E).
        6. Blocked? Pick a perpendicular OPEN exit or turn_left/turn_right — not the same move_* again.
        7. NO prose answers — always one game_act or game_sync tool call.
    """.trimIndent()

    fun systemPrompt(snapshot: GameSnapshot, style: AgentPromptStyle): String {
        val pose = snapshot.player.pose
        val px = pose.x
        val py = pose.y
        val keys = snapshot.keyPickups.joinToString { "(${it.x.toInt()},${it.y.toInt()})" }
            .ifBlank { "none visible" }
        val nearestMob = AgentCombatHelper.nearestMob(snapshot)
        val mobKinds = MapCellRenderer.mobKindCounts(snapshot)
        val mobLine = when {
            nearestMob == null -> "mobsOnLevel=0"
            else -> {
                val d = AgentCombatHelper.distanceToMob(pose, nearestMob)
                val aim = AgentCombatHelper.aimYaw(pose, nearestMob)
                "mobsOnLevel=${snapshot.mobs.size} kinds=[$mobKinds] nearest=${nearestMob.kind} " +
                    "dist=${"%.1f".format(d)} (inside rooms until you enter via D) " +
                    "aimYaw=${"%.2f".format(aim)}"
            }
        }
        val combatHint = combatInstructions(snapshot, nearestMob)
        val doorHint = doorNavigationHint(snapshot)
        val doorsInView = AgentDoorHelper.doorsInView(snapshot, radius = 2)
        val allDoors = AgentDoorHelper.formatAllDoorsList(snapshot)
        val targetDoor = targetDoor(snapshot)
        val doorCompass = targetDoor?.let { AgentDoorHelper.formatDoorCompass(snapshot, it) }
            ?: "No doors on level."
        val openExits = AgentDoorHelper.formatOpenExits(snapshot)
        val doors = snapshot.doorMarkers.count { it.prizeIsKey }
        val goalHint = when {
            snapshot.keysCollected >= snapshot.keysRequired && snapshot.exitGate != null ->
                "GO: exit ${snapshot.exitGate}"
            snapshot.roomClearTimer != null ->
                "GO: clear mob room (roomTimer active) — game_sync attack"
            snapshot.keyPickups.isEmpty() && doors > 0 ->
                "GO: enter keyRoom/mobRoom doors (D) with interact (E), collect keys, then exit"
            else -> "GO: collect keys then exit"
        }
        val timerLine = snapshot.roomClearTimer?.let {
            "roomTimerMs=${it.remainingMs}"
        } ?: "roomTimer=none"

        val map = when (style) {
            AgentPromptStyle.EMERGENCY -> localMap(snapshot, radius = 1)
            AgentPromptStyle.COMPACT -> localMap(snapshot, radius = 2)
        }

        return """
            Roguelike agent. Win: enter D doors → fight in rooms → collect keys → interact on exit gate.
            Tools:
            - game_act: $GRID_ACTIONS (explore, turn, open doors with interact)
            - game_sync: attack/reload/move — ONLY inside an active mob room (roomTimerMs set)
            move_north/south = ±x, move_east/west = ±y.
            $INTERACT_E_LINE
            NO text. ONLY one tool call per turn.

            $DOOR_RULES
            $COMBAT_RULES
            $WALL_NAVIGATION_RULES

            Navigation: if position unchanged, a wall blocked you (# or D). Use Corridor exits — detour along open tiles:
            north → east → south → west. Do NOT repeat the same move_* into a wall or D.

            $openExits
            $allDoors
            $doorCompass
            $doorsInView
            $doorHint

            $combatHint

            phase=${snapshot.phase} hp=${snapshot.player.hp}/${snapshot.player.maxHp}
            keys=${snapshot.keysCollected}/${snapshot.keysRequired} visible=[$keys]
            pos=(${px.toInt()},${py.toInt()}) yaw=${"%.2f".format(pose.yaw)} $mobLine
            ammo=${snapshot.player.ammo} $timerLine
            $goalHint exit=${snapshot.exitGate}

            Map (@=you K=key E=exit D=room seal/door #=wall .=floor M=melee R=ranged G=guard):
            $map
        """.trimIndent()
    }

    /** Nearest door the agent should head for — key rooms first when keys still needed. */
    internal fun targetDoor(snapshot: GameSnapshot): DoorMarkerSnapshot? {
        if (snapshot.doorMarkers.isEmpty()) return null
        val pose = snapshot.player.pose
        if (snapshot.keysCollected < snapshot.keysRequired) {
            snapshot.doorMarkers
                .filter { it.prizeIsKey || it.mobRoom }
                .minByOrNull { AgentDoorHelper.distanceToDoor(pose, it) }
                ?.let { return it }
        }
        return AgentDoorHelper.nearestDoor(snapshot)
    }

    /** Door/key/exit guidance derived from snapshot — keeps LLM oriented without full planner. */
    internal fun doorNavigationHint(snapshot: GameSnapshot): String {
        val pose = snapshot.player.pose

        if (snapshot.keysCollected >= snapshot.keysRequired) {
            snapshot.exitGate?.let { gate ->
                val dist = cellDistance(pose.x, pose.y, gate.x + 0.5f, gate.y + 0.5f)
                val onGate = floor(pose.x).toInt() == gate.x && floor(pose.y).toInt() == gate.y
                return if (onGate) {
                    "EXIT GATE at (${gate.x},${gate.y}) — game_act action=interact (E) NOW."
                } else {
                    "GOAL: stand ON exit cell (${gate.x},${gate.y}) dist=${fmt(dist)} — game_act move_* onto E, then interact."
                }
            }
        }

        snapshot.keyPickups.minByOrNull {
            hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble())
        }?.let { key ->
            val dist = hypot((key.x - pose.x).toDouble(), (key.y - pose.y).toDouble()).toFloat()
            if (dist <= InteractionConstants.INTERACT_RADIUS) {
                return "KEY at (${key.x.toInt()},${key.y.toInt()}) — game_act action=interact (E) to pick up."
            }
        }

        if (AgentDoorHelper.canPressE(snapshot)) {
            return "DOOR READY — game_act action=interact (E) NOW to enter room."
        }

        if (AgentDoorHelper.hasDoorSealInView(snapshot)) {
            val door = AgentDoorHelper.nearestVisibleDoor(snapshot)
                ?: return "D visible on map — explore toward it; do NOT interact at # walls."
            return formatDoorHint(snapshot, door)
        }

        val door = targetDoor(snapshot)
            ?: snapshot.doorMarkers.minByOrNull {
                hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble())
            }
            ?: return "Doors: explore with move_* until D appears in the map window."

        val dc = AgentDoorHelper.doorCell(door)
        val dist = AgentDoorHelper.distanceToDoor(pose, door)
        val label = AgentDoorHelper.doorLabel(door)
        val next = AgentDoorHelper.suggestApproachMove(snapshot, door)
        return "No D in map window — follow compass above toward ($dc.x,$dc.y) $label dist=${fmt(dist)}. " +
            "game_act action=$next (not interact until beside D)."
    }

    private fun formatDoorHint(snapshot: GameSnapshot, door: DoorMarkerSnapshot): String {
        val pose = snapshot.player.pose
        val dist = AgentDoorHelper.distanceToDoor(pose, door)
        val dc = AgentDoorHelper.doorCell(door)
        val label = AgentDoorHelper.doorLabel(door)
        val next = AgentDoorHelper.suggestDoorAction(snapshot, door)

        if (next == GameActions.INTERACT) {
            return "DOOR ($dc.x,$dc.y) $label — D is a wall; game_act action=interact (E) NOW to enter."
        }

        if (AgentDoorHelper.isAdjacentToDoorCell(pose, door)) {
            return "BESIDE D ($dc.x,$dc.y) $label — cannot walk into D. " +
                "game_act action=$next, then interact (E)."
        }

        if (AgentDoorHelper.isNearDoor(pose, door)) {
            return "D visible ($dc.x,$dc.y) $label dist=${fmt(dist)} — step to floor beside D: " +
                "game_act action=$next."
        }

        return "D visible ($dc.x,$dc.y) $label dist=${fmt(dist)} — approach floor beside D: " +
            "game_act action=$next, then face D + interact."
    }

    private fun cellDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    private fun fmt(value: Float): String = "%.1f".format(value)

    /** True when game_act action=interact (= key E) should succeed right now. */
    fun shouldInteractNow(snapshot: GameSnapshot): Boolean = AgentDoorHelper.canPressE(snapshot)

    fun interactDecision(sessionId: String): ToolCallDecision = gridActionDecision(sessionId, GameActions.INTERACT)

    fun gridActionDecision(sessionId: String, action: String): ToolCallDecision = ToolCallDecision(
        tool = "game_act",
        arguments = buildJsonObject {
            put("sessionId", sessionId)
            put("action", action)
        }.mapValues { it.value },
    )

    fun doorActionDecision(sessionId: String, snapshot: GameSnapshot): ToolCallDecision {
        val door = AgentDoorHelper.nearestVisibleDoor(snapshot)
            ?: AgentDoorHelper.nearestDoor(snapshot)
            ?: return interactDecision(sessionId)
        return gridActionDecision(sessionId, AgentDoorHelper.suggestDoorAction(snapshot, door))
    }

    private fun combatInstructions(snapshot: GameSnapshot, nearestMob: ru.course.roguelike.shared.dto.MobSnapshot?): String {
        if (AgentCombatHelper.inActiveCombatRoom(snapshot)) {
            val mob = nearestMob ?: return "Combat: room active — game_sync attack=true toward visible mobs."
            val pose = snapshot.player.pose
            val dist = AgentCombatHelper.distanceToMob(pose, mob)
            val aim = AgentCombatHelper.aimYaw(pose, mob)
            val pitch = AgentCombatHelper.aimPitch(pose, mob)
            val flyHint = if (mob.z > 0.01f) " (flying mob — pitch up)" else ""
            return when {
                snapshot.player.ammo <= 0 ->
                    "Combat: IN ROOM, OUT OF AMMO — game_sync reload=true deltaMs=120."
                dist <= AgentCombatHelper.ROOM_COMBAT_RANGE ->
                    "Combat: mob room active — game_sync attack=true clientYaw=${
                        "%.2f".format(aim)
                    } clientPitch=${"%.2f".format(pitch)}$flyHint deltaMs=120."
                else ->
                    "Combat: inside room — game_sync attack=true clientYaw=${"%.2f".format(aim)} " +
                        "clientPitch=${"%.2f".format(pitch)}$flyHint deltaMs=120."
            }
        }
        if (nearestMob == null) {
            return "Combat: no mobs on level — use game_act move_* toward nearest D door, then interact (E)."
        }
        return "Combat: roomTimer=none — mobs are behind D walls. Do NOT attack. " +
            "Use game_act toward D, then interact (E) to enter and start the fight."
    }

    fun stuckUserMessage(snapshot: GameSnapshot, repeatedAction: String): String {
        val combat = if (AgentCombatHelper.inActiveCombatRoom(snapshot)) {
            " room active — try game_sync attack=true with aimYaw and clientPitch from prompt."
        } else {
            " find D door and game_act action=interact when DOOR READY."
        }
        return "STUCK repeating '$repeatedAction'. Pick a DIFFERENT action.$combat " +
            "keys=${snapshot.keysCollected}/${snapshot.keysRequired} " +
            "visible keys=${snapshot.keyPickups.size} mobsOnLevel=${snapshot.mobs.size}"
    }

    /** Shown to LLM when observe pos unchanged — before code override kicks in. */
    fun positionStuckUserMessage(
        snapshot: GameSnapshot,
        samePosStreak: Int,
        suggestedMove: String,
        lastMove: String?,
    ): String {
        AgentDoorHelper.doorStuckHint(snapshot)?.let { return it }
        val pose = snapshot.player.pose
        val blocked = lastMove?.let { " Last move '$it' hit a wall or door D." } ?: ""
        val exits = AgentDoorHelper.formatOpenExits(snapshot)
        val door = doorNavigationHint(snapshot)
        return "BLOCKED at (${pose.x.toInt()},${pose.y.toInt()}) for $samePosStreak steps.$blocked " +
            "Detour along an OPEN corridor exit — do not bash the same wall. " +
            "Call game_act with action=$suggestedMove (pathfinder detour, not ${lastMove ?: "blocked dir"}). " +
            "$exits $door"
    }

    fun suggestRotateMove(stepIndex: Int, samePosStreak: Int, lastMove: String?, snapshot: GameSnapshot): String =
        AgentDoorHelper.suggestUnstuckMove(snapshot, lastMove, stepIndex + samePosStreak)

    private fun localMap(snapshot: GameSnapshot, radius: Int): String {
        val playerX = snapshot.player.pose.x.toInt()
        val playerY = snapshot.player.pose.y.toInt()
        return buildString {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    append(cellChar(snapshot, playerX + dx, playerY + dy, playerX, playerY))
                }
                append('\n')
            }
        }.trimEnd()
    }

    private fun cellChar(snapshot: GameSnapshot, x: Int, y: Int, playerX: Int, playerY: Int): Char =
        MapCellRenderer.charAt(snapshot, x, y, playerX, playerY)
}
