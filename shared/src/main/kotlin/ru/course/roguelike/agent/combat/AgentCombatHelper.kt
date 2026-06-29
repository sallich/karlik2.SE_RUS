package ru.course.roguelike.agent.combat

import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.combat.CombatAim
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose

object AgentCombatHelper {
    /** Mob must be this close to force combat outside an active room. */
    const val THREAT_RANGE = 3.5f

    /** In a timed mob room, engage up to this distance. */
    const val ROOM_COMBAT_RANGE = 5f

    private const val MELEE_KITE_RANGE = 2f
    private const val COMBAT_DELTA_MS = 120

    fun nearestMob(snapshot: GameSnapshot): MobSnapshot? {
        val pose = snapshot.player.pose
        return snapshot.mobs.minByOrNull {
            hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble())
        }
    }

    fun distanceToMob(pose: PlayerPose, mob: MobSnapshot): Float =
        hypot((mob.x - pose.x).toDouble(), (mob.y - pose.y).toDouble()).toFloat()

    fun aimYaw(pose: PlayerPose, mob: MobSnapshot): Float =
        atan2((mob.y - pose.y).toDouble(), (mob.x - pose.x).toDouble()).toFloat()

    fun aimPitch(pose: PlayerPose, mob: MobSnapshot): Float =
        CombatAim.pitchToTarget(pose, mob.x, mob.y, CombatAim.mobHitCenterZ(mob.z))

    fun inActiveCombatRoom(snapshot: GameSnapshot): Boolean =
        snapshot.roomClearTimer != null

    /**
     * True only when fighting makes sense — not for every mob on the global map.
     */
    fun shouldEngage(snapshot: GameSnapshot): Boolean {
        val mob = nearestMob(snapshot) ?: return false
        val dist = distanceToMob(snapshot.player.pose, mob)
        return when {
            dist <= THREAT_RANGE -> true
            inActiveCombatRoom(snapshot) && dist <= ROOM_COMBAT_RANGE -> true
            else -> false
        }
    }

    fun needsReload(snapshot: GameSnapshot): Boolean =
        snapshot.player.ammo <= 0 && shouldEngage(snapshot)

    fun combatDecision(sessionId: String, snapshot: GameSnapshot): ToolCallDecision {
        if (needsReload(snapshot)) {
            return reloadDecision(sessionId)
        }
        val mob = nearestMob(snapshot)
            ?: return waitDecision(sessionId)
        val pose = snapshot.player.pose
        val yaw = aimYaw(pose, mob)
        val pitch = aimPitch(pose, mob)
        val dist = distanceToMob(pose, mob)
        val kite = mob.kind == MobKind.MELEE && dist <= MELEE_KITE_RANGE
        return attackDecision(sessionId, yaw, pitch, backward = kite)
    }

    /** Shoot while stepping back — used when kiting melee at close range with room to retreat. */
    fun kiteDecision(sessionId: String, snapshot: GameSnapshot): ToolCallDecision {
        if (needsReload(snapshot)) {
            return reloadDecision(sessionId)
        }
        val mob = nearestMob(snapshot) ?: return waitDecision(sessionId)
        val pose = snapshot.player.pose
        val yaw = aimYaw(pose, mob)
        val pitch = aimPitch(pose, mob)
        return attackDecision(sessionId, yaw, pitch, backward = true)
    }

    /**
     * Unstick from a corner/wall during combat: strafe + forward while shooting (no backward).
     */
    fun repositionDecision(sessionId: String, snapshot: GameSnapshot, stepIndex: Int): ToolCallDecision {
        if (needsReload(snapshot)) {
            return reloadDecision(sessionId)
        }
        val mob = nearestMob(snapshot) ?: return waitDecision(sessionId)
        val pose = snapshot.player.pose
        val yaw = aimYaw(pose, mob)
        val pitch = aimPitch(pose, mob)
        val strafeLeft = stepIndex % 2 == 0
        return ToolCallDecision(
            tool = "game_sync",
            arguments = syncArgs(sessionId) {
                put("clientYaw", yaw)
                put("clientPitch", pitch)
                put("attack", true)
                put("forward", true)
                put("strafeLeft", strafeLeft)
                put("strafeRight", !strafeLeft)
                put("deltaMs", COMBAT_DELTA_MS)
            },
        )
    }

    /**
     * Code may force combat only in an active mob room or when a mob is in melee threat range.
     */
    fun shouldForceCombatOverride(snapshot: GameSnapshot): Boolean {
        if (!shouldEngage(snapshot)) return false
        if (inActiveCombatRoom(snapshot)) return true
        val mob = nearestMob(snapshot) ?: return false
        return distanceToMob(snapshot.player.pose, mob) <= THREAT_RANGE
    }

    private fun attackDecision(sessionId: String, yaw: Float, pitch: Float, backward: Boolean): ToolCallDecision =
        ToolCallDecision(
            tool = "game_sync",
            arguments = syncArgs(sessionId) {
                put("clientYaw", yaw)
                put("clientPitch", pitch)
                put("attack", true)
                put("backward", backward)
                put("deltaMs", COMBAT_DELTA_MS)
            },
        )

    fun reloadDecision(sessionId: String): ToolCallDecision = ToolCallDecision(
        tool = "game_sync",
        arguments = syncArgs(sessionId) {
            put("reload", true)
            put("deltaMs", COMBAT_DELTA_MS)
        },
    )

    private fun waitDecision(sessionId: String): ToolCallDecision = ToolCallDecision(
        tool = "game_sync",
        arguments = syncArgs(sessionId) {
            put("deltaMs", COMBAT_DELTA_MS)
        },
    )

    private fun syncArgs(
        sessionId: String,
        block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): Map<String, JsonElement> = buildJsonObject {
        put("sessionId", sessionId)
        block()
    }.mapValues { it.value }
}
