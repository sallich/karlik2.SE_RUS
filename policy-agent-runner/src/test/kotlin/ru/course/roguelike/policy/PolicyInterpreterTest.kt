package ru.course.roguelike.policy

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.dsl.ObjectiveKinds
import ru.course.roguelike.policy.dsl.PolicyConditions
import ru.course.roguelike.policy.dsl.PolicyInterpreter
import ru.course.roguelike.policy.dsl.PolicyObjective
import ru.course.roguelike.policy.planner.PolicyKeyHuntPlanner
import ru.course.roguelike.policy.planner.PolicyRoomExitPlanner
import ru.course.roguelike.policy.llm.PolicyJson
import ru.course.roguelike.policy.llm.PolicyMerger
import ru.course.roguelike.policy.llm.PolicyPromptBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.PlayerSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.SessionPhase
import ru.course.roguelike.shared.dto.DoorMarkerSnapshot
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.RoomClearTimerSnapshot
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.protocol.GameActions

class PolicyInterpreterTest {
    @Test
    fun `interpreter picks visible key navigation`() {
        val snapshot = simpleRoom()
        val ctx = PolicyContext()
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshot, "s1", ctx)
        assertTrue(result.decision.tool == "game_act" || result.decision.tool == "game_sync")
    }

    @Test
    fun `ping pong between two cells is trapped`() {
        val ctx = PolicyContext()
        ctx.updateAfterStep(snap(1, 1), snap(1, 2), waitDecision(), false)
        ctx.updateAfterStep(snap(1, 2), snap(1, 1), waitDecision(), false)
        ctx.updateAfterStep(snap(1, 1), snap(1, 2), waitDecision(), false)
        ctx.updateAfterStep(snap(1, 2), snap(1, 1), waitDecision(), false)
        assertTrue(ctx.isPingPong())
        assertTrue(ctx.isTrapped())
    }

    @Test
    fun `merger allows llm action swap for needs_keys`() {
        val llm = DefaultPolicies.standard().copy(
            rules = DefaultPolicies.standardRules().map { rule ->
                if (rule.whenClause == PolicyConditions.NEEDS_KEYS) {
                    rule.copy(action = "explore_unvisited", enabled = true)
                } else {
                    rule
                }
            },
        )
        val merged = PolicyMerger.mergeWithBaseline(llm)
        val needsKeys = merged.rules.first { it.whenClause == PolicyConditions.NEEDS_KEYS }
        assertEquals("explore_unvisited", needsKeys.action)
    }

    @Test
    fun `does not seek exit while room timer active with mobs`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 2.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        ctx.seekRoomExit = true
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), inRoom, "s1", ctx)
        assertEquals(PolicyConditions.COMBAT_IN_ROOM, result.condition)
        assertEquals("game_sync", result.decision.tool)
    }

    @Test
    fun `timer drop with living mobs in room does not seek exit`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val mob = MobSnapshot(
            id = 1L,
            kind = MobKind.MELEE,
            x = 3.5f,
            y = 2.5f,
            hp = 10,
            maxHp = 10,
        )
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(mob),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        val leftRoomTimer = inRoom.copy(roomClearTimer = null, mobs = listOf(mob))
        ctx.updateAfterStep(inRoom, leftRoomTimer, waitDecision(), false)
        assertTrue(!ctx.seekRoomExit)
    }

    @Test
    fun `seekRoomExit persists on doorway adjacent to frozen region`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 2.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        val cleared = inRoom.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom, cleared, waitDecision(), false)
        assertTrue(ctx.seekRoomExit)
        val doorway = cleared.copy(
            player = cleared.player.copy(pose = PlayerPose(x = 0.5f, y = 2.5f, yaw = 0f, pitch = 0f)),
        )
        assertTrue(ctx.isInsideMobRoomForExit(doorway))
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), doorway, "s1", ctx)
        assertEquals(PolicyConditions.NEEDS_ROOM_EXIT, result.condition)
        assertTrue(!PolicyInterpreter.matches(PolicyConditions.NEEDS_KEYS, doorway, ctx))
    }

    @Test
    fun `seekRoomExit after clear when frozen region captured lazily`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 2.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.seedFromSnapshot(inRoom)
        val cleared = inRoom.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom, cleared, waitDecision(), false)
        assertTrue(ctx.seekRoomExit)
        assertTrue(!ctx.frozenRoomRegion.isNullOrEmpty())
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), cleared, "s1", ctx)
        assertEquals(PolicyConditions.NEEDS_ROOM_EXIT, result.condition)
        assertTrue(result.decision.tool == "game_sync" || result.decision.tool == "game_act")
    }

    @Test
    fun `mobRoomExitPending forces exit when seekRoomExit cleared incorrectly`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 2.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        val cleared = inRoom.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom, cleared, waitDecision(), false)
        assertTrue(ctx.mobRoomExitPending)
        ctx.seekRoomExit = false
        val corner = cleared.copy(
            player = cleared.player.copy(pose = PlayerPose(x = 5.5f, y = 3.5f, yaw = 1.5f, pitch = 0f)),
        )
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), corner, "s1", ctx)
        assertEquals(PolicyConditions.NEEDS_ROOM_EXIT, result.condition)
        assertEquals("game_sync", result.decision.tool)
    }

    @Test
    fun `new mob room resets frozen region and exit pending`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before1 = snapshot.copy(roomClearTimer = null)
        val inRoom1 = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 2.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before1, inRoom1, waitDecision(), false)
        val region1 = ctx.frozenRoomRegion
        val cleared1 = inRoom1.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom1, cleared1, waitDecision(), false)
        assertTrue(ctx.mobRoomExitPending)

        val inRoom2 = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 2L,
                    kind = MobKind.MELEE,
                    x = 4.5f,
                    y = 3.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
            player = cleared1.player.copy(
                pose = PlayerPose(x = 4.5f, y = 3.5f, yaw = 0f, pitch = 0f),
            ),
        )
        ctx.updateAfterStep(cleared1, inRoom2, waitDecision(), false)
        assertTrue(!ctx.mobRoomExitPending)
        assertTrue(!region1.isNullOrEmpty())
        assertTrue(!ctx.frozenRoomRegion.isNullOrEmpty())
    }

    @Test
    fun `needs room exit when seekRoomExit set`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 3.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        val cleared = inRoom.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom, cleared, waitDecision(), false)
        assertTrue(ctx.seekRoomExit)
        assertTrue(PolicyInterpreter.matches(PolicyConditions.NEEDS_ROOM_EXIT, cleared, ctx))
    }

    @Test
    fun `stuck does not match while leaving room`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 3.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        val cleared = inRoom.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom, cleared, waitDecision(), false)
        ctx.updateAfterStep(cleared, cleared, waitDecision(), false)
        ctx.updateAfterStep(cleared, cleared, waitDecision(), false)
        ctx.updateAfterStep(cleared, cleared, waitDecision(), false)
        assertTrue(ctx.isTrapped())
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), cleared, "s1", ctx)
        assertEquals(PolicyConditions.NEEDS_ROOM_EXIT, result.condition)
    }

    @Test
    fun `seekRoomExit respects disabled needs_room_exit rule`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(
                    id = 1L,
                    kind = MobKind.MELEE,
                    x = 3.5f,
                    y = 2.5f,
                    hp = 10,
                    maxHp = 10,
                ),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        val cleared = inRoom.copy(roomClearTimer = null, mobs = emptyList())
        ctx.updateAfterStep(inRoom, cleared, waitDecision(), false)
        assertTrue(ctx.seekRoomExit)
        val policy = DefaultPolicies.standard().copy(
            rules = DefaultPolicies.standardRules().map { rule ->
                if (rule.whenClause == PolicyConditions.NEEDS_ROOM_EXIT) {
                    rule.copy(enabled = false)
                } else {
                    rule
                }
            },
        )
        val result = PolicyInterpreter.interpret(policy, cleared, "s1", ctx)
        assertTrue(result.condition != PolicyConditions.NEEDS_ROOM_EXIT)
    }

    @Test
    fun `needs keys blocked while seekRoomExit`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        ctx.seekRoomExit = true
        assertTrue(!PolicyInterpreter.matches(PolicyConditions.NEEDS_KEYS, snapshot, ctx))
    }

    @Test
    fun `committed objective drives navigation over fallback rules`() {
        val snapshot = plainRoom()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.EXPLORE, target = "3,2", commitSteps = 20))
        assertTrue(ctx.isObjectiveValid(snapshot))
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshot, "s1", ctx)
        assertEquals("objective:explore", result.condition)
        assertTrue(result.decision.tool == "game_sync" || result.decision.tool == "game_act")
    }

    @Test
    fun `objective stays committed across steps (no flip)`() {
        val snapshot = plainRoom()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.EXPLORE, target = "3,2", commitSteps = 20))
        val first = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshot, "s1", ctx)
        val second = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshot, "s1", ctx)
        assertEquals(first.condition, second.condition)
        assertEquals("objective:explore", second.condition)
    }

    @Test
    fun `reactive guard overrides committed objective`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        val before = snapshot.copy(roomClearTimer = null)
        val inRoom = snapshot.copy(
            roomClearTimer = RoomClearTimerSnapshot(remainingMs = 30_000, totalMs = 60_000),
            mobs = listOf(
                MobSnapshot(id = 1L, kind = MobKind.MELEE, x = 3.5f, y = 2.5f, hp = 10, maxHp = 10),
            ),
        )
        ctx.updateAfterStep(before, inRoom, waitDecision(), false)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.ENTER_DOOR, target = "0,2", commitSteps = 30))
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), inRoom, "s1", ctx)
        assertEquals(PolicyConditions.COMBAT_IN_ROOM, result.condition)
    }

    @Test
    fun `near visible key guard overrides explore objective`() {
        val snapshot = simpleRoom().copy(
            player = simpleRoom().player.copy(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
            ),
        )
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.EXPLORE, target = "1,1", commitSteps = 30))
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshot, "s1", ctx)
        assertEquals(PolicyConditions.NEAR_VISIBLE_KEY, result.condition)
        assertEquals("game_sync", result.decision.tool)
    }

    @Test
    fun `objective needs replan when target reached`() {
        val snapshot = simpleRoom()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.EXPLORE, target = "2,2", commitSteps = 20))
        assertTrue(ctx.objectiveNeedsReplan(snapshot))
        assertTrue(!ctx.isObjectiveValid(snapshot))
    }

    @Test
    fun `objective expires after commit window elapses`() {
        val snapshot = simpleRoom()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.EXPLORE, target = "3,2", commitSteps = 5))
        repeat(5) { ctx.updateAfterStep(snapshot, snapshot, waitDecision(), false) }
        assertTrue(ctx.objectiveNeedsReplan(snapshot))
        assertTrue(!ctx.isObjectiveValid(snapshot))
    }

    @Test
    fun `objective with unknown target falls back to rules`() {
        val snapshot = simpleRoom()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.setActiveObjective(PolicyObjective(ObjectiveKinds.ENTER_DOOR, target = "99,99", commitSteps = 20))
        assertTrue(!ctx.isObjectiveValid(snapshot))
        val result = PolicyInterpreter.interpret(DefaultPolicies.standard(), snapshot, "s1", ctx)
        assertTrue(!result.condition.startsWith("objective:"))
    }

    @Test
    fun `sealed column hall has no resolvable exit`() {
        // plainRoom is fully walled with no mob-door marker: a column/elevator hall misread as a room.
        assertTrue(!PolicyRoomExitPlanner.hasResolvableExit(plainRoom(), null))
    }

    @Test
    fun `mob room with door keeps a resolvable exit`() {
        assertTrue(PolicyRoomExitPlanner.hasResolvableExit(mobRoomSnapshot(), null))
    }

    @Test
    fun `false room exit is released when stuck with no real exit`() {
        val snapshot = plainRoom()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.seekRoomExit = true
        ctx.mobRoomExitPending = true
        repeat(5) { ctx.recordMatchedCondition(PolicyConditions.NEEDS_ROOM_EXIT) }
        assertTrue(ctx.isRoomExitStuck())
        ctx.releaseFalseRoomExitIfStuck(snapshot)
        assertTrue(!ctx.seekRoomExit)
        assertTrue(!ctx.mobRoomExitPending)
    }

    @Test
    fun `real mob room exit is not released on brief stuck`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.seekRoomExit = true
        repeat(5) { ctx.recordMatchedCondition(PolicyConditions.NEEDS_ROOM_EXIT) }
        assertTrue(ctx.isRoomExitStuck())
        ctx.releaseFalseRoomExitIfStuck(snapshot)
        assertTrue(ctx.seekRoomExit)
    }

    @Test
    fun `room exit is hard-released after sustained thrashing even with a reachable door`() {
        val snapshot = mobRoomSnapshot()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        ctx.seekRoomExit = true
        ctx.mobRoomExitPending = true
        repeat(9) { ctx.recordMatchedCondition(PolicyConditions.NEEDS_ROOM_EXIT) }
        ctx.releaseFalseRoomExitIfStuck(snapshot)
        assertTrue(!ctx.seekRoomExit)
        assertTrue(!ctx.mobRoomExitPending)
    }

    @Test
    fun `loot door with no seal tile becomes dead after repeated blocks`() {
        val snapshot = lootDoorNoSeal()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        assertTrue(PolicyInterpreter.matches(PolicyConditions.AT_DOOR_NEED_ENTER, snapshot, ctx))
        repeat(3) { ctx.updateDoorInteraction(snapshot) }
        assertTrue(ctx.isNearestDoorDead(snapshot))
        assertTrue(!PolicyInterpreter.matches(PolicyConditions.AT_DOOR_NEED_ENTER, snapshot, ctx))
    }

    @Test
    fun `phantom loot door is marked dead on first blocked observation`() {
        val snapshot = lootDoorNoSeal()
        val ctx = PolicyContext()
        ctx.updateDoorInteraction(snapshot)
        assertTrue(ctx.isNearestDoorDead(snapshot))
    }

    @Test
    fun `key hunt does not blind interact on approach tile when E is not ready`() {
        val snapshot = lootDoorNoSeal()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        val decision = PolicyKeyHuntPlanner.plan("s1", snapshot, ctx.knowledge, context = ctx)
        val action = decision.arguments["action"]?.toString()?.trim('"')
        assertTrue(action != GameActions.INTERACT)
    }

    @Test
    fun `key hunt interacts when standing on key cell`() {
        val snapshot = simpleRoom().copy(
            player = simpleRoom().player.copy(
                pose = PlayerPose(x = 3.5f, y = 2.5f, yaw = 0f, pitch = 0f),
            ),
        )
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        val decision = PolicyKeyHuntPlanner.plan("s1", snapshot, ctx.knowledge, strictFairPlay = false, context = ctx)
        assertEquals("game_act", decision.tool)
        assertEquals(GameActions.INTERACT, decision.arguments["action"]?.toString()?.trim('"'))
    }

    @Test
    fun `key hunt steps toward key when adjacent but out of interact radius`() {
        val snapshot = simpleRoom().copy(
            player = simpleRoom().player.copy(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
            ),
        )
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        val decision = PolicyKeyHuntPlanner.plan("s1", snapshot, ctx.knowledge, strictFairPlay = false, context = ctx)
        assertEquals("game_sync", decision.tool)
        assertEquals(true, decision.arguments["forward"]?.toString()?.toBooleanStrictOrNull())
    }

    @Test
    fun `key hunt interacts when within interact radius`() {
        val snapshot = simpleRoom().copy(
            player = simpleRoom().player.copy(
                pose = PlayerPose(x = 3.0f, y = 2.5f, yaw = 0f, pitch = 0f),
            ),
        )
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        val decision = PolicyKeyHuntPlanner.plan("s1", snapshot, ctx.knowledge, strictFairPlay = false, context = ctx)
        assertEquals("game_act", decision.tool)
        assertEquals(GameActions.INTERACT, decision.arguments["action"]?.toString()?.trim('"'))
    }

    @Test
    fun `fallback routes to frontier when door is dead and blocked`() {
        val snapshot = lootDoorNoSeal()
        val ctx = PolicyContext()
        ctx.markDoorDead(snapshot.doorMarkers.first())
        val policy = DefaultPolicies.standard().copy(
            rules = DefaultPolicies.standard().rules.map { it.copy(enabled = false) },
        )
        val result = PolicyInterpreter.interpret(policy, snapshot, "s1", ctx)
        assertEquals("fallback", result.condition)
        assertEquals("game_sync", result.decision.tool)
    }

    @Test
    fun `proper seal door is pressable and never marked dead`() {
        val snapshot = sealDoorSnapshot()
        val ctx = PolicyContext()
        ctx.seedFromSnapshot(snapshot)
        repeat(5) { ctx.updateDoorInteraction(snapshot) }
        assertTrue(!ctx.isNearestDoorDead(snapshot))
        assertTrue(PolicyInterpreter.matches(PolicyConditions.AT_DOOR_READY, snapshot, ctx))
    }

    private fun doorRoomTiles(width: Int, height: Int): MutableList<TileType> = buildList {
        repeat(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            add(
                when {
                    x == 0 || y == 0 || x == width - 1 || y == height - 1 -> TileType.WALL
                    else -> TileType.FLOOR
                },
            )
        }
    }.toMutableList()

    /** Loot-door marker at (3,4) with NO ROOM_SEAL tile: canPressE can never be true. */
    private fun lootDoorNoSeal(): GameSnapshot {
        val width = 7
        val height = 7
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = width,
            height = height,
            tiles = doorRoomTiles(width, height),
            player = PlayerSnapshot(
                pose = PlayerPose(x = 3.5f, y = 3.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
            doorMarkers = listOf(
                DoorMarkerSnapshot(x = 3.5f, y = 4.5f, kind = ItemKind.WEAPON_SHOTGUN),
            ),
        )
    }

    /** Real ROOM_SEAL tile at (3,4) with a matching marker: pressable via E. */
    private fun sealDoorSnapshot(): GameSnapshot {
        val width = 7
        val height = 7
        val tiles = doorRoomTiles(width, height)
        tiles[4 * width + 3] = TileType.ROOM_SEAL
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = width,
            height = height,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 3.5f, y = 3.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
            doorMarkers = listOf(
                DoorMarkerSnapshot(x = 3.5f, y = 4.5f, kind = ItemKind.WEAPON_SHOTGUN),
            ),
        )
    }

    @Test
    fun `merger keeps canonical order even if llm reorders`() {
        val shuffled = DefaultPolicies.standard().copy(
            rules = DefaultPolicies.standardRules().reversed(),
        )
        val merged = PolicyMerger.mergeWithBaseline(shuffled)
        assertEquals(PolicyConditions.COMBAT_KITE, merged.rules.first().whenClause)
        assertEquals(PolicyConditions.NEEDS_RELOAD, merged.rules[1].whenClause)
        assertEquals(PolicyConditions.EXPLORE, merged.rules.last().whenClause)
        assertEquals(19, merged.rules.size)
    }

    @Test
    fun `sanitize drops unknown rules`() {
        val raw = DefaultPolicies.standard().copy(
            rules = listOf(
                ru.course.roguelike.policy.dsl.PolicyRule("fly_away", "teleport"),
                ru.course.roguelike.policy.dsl.PolicyRule(PolicyConditions.EXPLORE, "explore"),
            ),
        )
        val clean = PolicyPromptBuilder.sanitize(raw)
        assertEquals(1, clean.rules.size)
        assertEquals(PolicyConditions.EXPLORE, clean.rules.first().whenClause)
    }

    @Test
    fun `policy json roundtrip`() {
        val policy = DefaultPolicies.standard()
        val text = PolicyJson.encode(policy)
        val parsed = PolicyJson.parse(text)
        assertEquals(policy.rules.size, parsed?.rules?.size)
    }

    @Test
    fun `tracker page is served`() = testApplication {
        application { policyModule() }
        val response = client.get("/api/v1/policy-agent/tracker")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Policy Agent Map Tracker"))
    }

    @Test
    fun `live endpoint returns json`() = testApplication {
        application { policyModule() }
        val response = client.get("/api/v1/policy-agent/live")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("running"))
    }

    @Test
    fun `health exposes policy-agent-runner`() = testApplication {
        application { policyModule() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("policy-agent-runner"))
        assertTrue(response.bodyAsText().contains("policy-dsl"))
    }

    private fun mobRoomSnapshot(): GameSnapshot {
        val width = 7
        val height = 5
        val tiles = buildList {
            repeat(width * height) { idx ->
                val x = idx % width
                val y = idx / width
                add(
                    when {
                        x == 0 || y == 0 || x == width - 1 || y == height - 1 -> TileType.WALL
                        else -> TileType.FLOOR
                    },
                )
            }
        }.toMutableList()
        tiles[2 * width + 0] = TileType.FLOOR
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = width,
            height = height,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 3.5f, y = 2.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
                ammo = 12,
                maxAmmo = 12,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 3,
            keyPickups = emptyList(),
            doorMarkers = listOf(
                DoorMarkerSnapshot(x = 0.5f, y = 2.5f, mobRoom = true),
            ),
        )
    }

    private fun simpleRoom(): GameSnapshot {
        val width = 5
        val height = 5
        val tiles = buildList {
            repeat(width * height) { idx ->
                val x = idx % width
                val y = idx / width
                add(
                    when {
                        x == 0 || y == 0 || x == width - 1 || y == height - 1 -> TileType.WALL
                        else -> TileType.FLOOR
                    },
                )
            }
        }
        return GameSnapshot(
            sessionId = "s1",
            seed = 1L,
            phase = SessionPhase.EXPLORATION.name,
            width = width,
            height = height,
            tiles = tiles,
            player = PlayerSnapshot(
                pose = PlayerPose(x = 2.5f, y = 2.5f, yaw = 0f, pitch = 0f),
                hp = 100,
                maxHp = 100,
            ),
            tick = 0L,
            keysCollected = 0,
            keysRequired = 1,
            keyPickups = listOf(KeySnapshot(id = 1, x = 3.5f, y = 2.5f)),
            exitGate = GridPos(3, 3),
        )
    }

    /** Plain walled room with no keys/doors/exit so no reactive guard can preempt an objective. */
    private fun plainRoom(): GameSnapshot =
        simpleRoom().copy(keyPickups = emptyList(), exitGate = null)

    private fun snap(x: Int, y: Int): GameSnapshot =
        simpleRoom().copy(
            player = simpleRoom().player.copy(
                pose = PlayerPose(x = x + 0.5f, y = y + 0.5f, yaw = 0f, pitch = 0f),
            ),
        )

    @Test
    fun `room exit successful sync does not blacklist target cells`() {
        val ctx = PolicyContext()
        ctx.seekRoomExit = true
        ctx.mobRoomExitPending = true
        val before = snapFromMob(2, 2)
        val after = snapFromMob(3, 2)
        ctx.noteUnstuckTarget("3,2")
        ctx.updateAfterStep(before, after, syncDecision(), false)
        assertTrue(ctx.failedUnstuckTargets().isEmpty())
    }

    @Test
    fun `room exit ping pong clears failed unstuck targets`() {
        val ctx = PolicyContext()
        ctx.seekRoomExit = true
        ctx.mobRoomExitPending = true
        val a = snapFromMob(2, 2)
        val b = snapFromMob(3, 2)
        ctx.noteUnstuckTarget("3,2")
        ctx.updateAfterStep(a, a, syncDecision(), false)
        assertTrue("3,2" in ctx.failedUnstuckTargets())
        ctx.updateAfterStep(a, b, syncDecision(), false)
        ctx.updateAfterStep(b, a, syncDecision(), false)
        ctx.updateAfterStep(a, b, syncDecision(), false)
        ctx.updateAfterStep(b, a, syncDecision(), false)
        assertTrue(ctx.isPingPong())
        assertTrue(ctx.failedUnstuckTargets().isEmpty())
    }

    private fun snapFromMob(x: Int, y: Int): GameSnapshot =
        mobRoomSnapshot().copy(
            player = mobRoomSnapshot().player.copy(
                pose = PlayerPose(x = x + 0.5f, y = y + 0.5f, yaw = 0f, pitch = 0f),
            ),
        )

    private fun syncDecision() = ToolCallDecision(
        tool = "game_sync",
        arguments = buildJsonObject { put("forward", true) }.mapValues { it.value },
    )

    private fun waitDecision() = ToolCallDecision(
        tool = "game_act",
        arguments = buildJsonObject { put("action", "wait") }.mapValues { it.value },
    )
}
