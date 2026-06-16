package ru.course.roguelike.policy.planner

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.course.roguelike.agent.combat.AgentCombatHelper
import ru.course.roguelike.agent.explore.AgentDoorHelper
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.policy.dsl.PolicyParams
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.PlayerPose
import kotlin.math.floor

/**
 * Policy-agent combat micro. Default [PolicyParams.COMBAT_PLANT] avoids orbit vs moving bosses.
 * LLM may set `params.combatStyle` to `kite` or `chase` on replan.
 */
object PolicyCombatHelper {
    fun combatDecision(
        sessionId: String,
        snapshot: GameSnapshot,
        combatStyle: String = PolicyParams.COMBAT_PLANT,
    ): ToolCallDecision {
        if (AgentCombatHelper.needsReload(snapshot)) {
            return reloadDecision(sessionId, snapshot, combatStyle)
        }
        val mob = AgentCombatHelper.nearestMob(snapshot)
            ?: return AgentCombatHelper.combatDecision(sessionId, snapshot)
        return when (combatStyle) {
            PolicyParams.COMBAT_KITE -> AgentCombatHelper.kiteDecision(sessionId, snapshot)
            PolicyParams.COMBAT_CHASE -> chaseAndShoot(sessionId, snapshot.player.pose, mob)
            else -> when {
                shouldPlantAndShoot(snapshot, mob) -> plantAndShoot(sessionId, snapshot.player.pose, mob)
                else -> AgentCombatHelper.combatDecision(sessionId, snapshot)
            }
        }
    }

    fun repositionDecision(
        sessionId: String,
        snapshot: GameSnapshot,
        stepIndex: Int,
        combatStyle: String = PolicyParams.COMBAT_PLANT,
    ): ToolCallDecision {
        if (AgentCombatHelper.needsReload(snapshot)) {
            return reloadDecision(sessionId, snapshot, combatStyle)
        }
        if (combatStyle == PolicyParams.COMBAT_CHASE) {
            return combatDecision(sessionId, snapshot, combatStyle)
        }
        if (!isCornerTrapped(snapshot)) {
            return combatDecision(sessionId, snapshot, combatStyle)
        }
        return AgentCombatHelper.repositionDecision(sessionId, snapshot, stepIndex)
    }

    fun reloadDecision(
        sessionId: String,
        snapshot: GameSnapshot,
        combatStyle: String = PolicyParams.COMBAT_PLANT,
    ): ToolCallDecision {
        val mob = AgentCombatHelper.nearestMob(snapshot)
        if (mob != null && AgentCombatHelper.shouldEngage(snapshot)) {
            val pose = snapshot.player.pose
            val dist = AgentCombatHelper.distanceToMob(pose, mob)
            val inRoom = AgentCombatHelper.inActiveCombatRoom(snapshot)
            val criticalHp = snapshot.player.hp <= CRITICAL_HP ||
                snapshot.player.hp <= snapshot.player.maxHp * CRITICAL_HP_RATIO
            val kiteStyle = combatStyle == PolicyParams.COMBAT_KITE
            val styleAllowsMove = kiteStyle || combatStyle == PolicyParams.COMBAT_CHASE
            val kiteReload = dist <= MELEE_BACKUP_RANGE && mob.kind == MobKind.MELEE
            val shouldBackpedal = kiteReload && (styleAllowsMove || criticalHp)
            val shouldMove = (inRoom && dist <= AgentCombatHelper.ROOM_COMBAT_RANGE || criticalHp) && styleAllowsMove
            if (shouldBackpedal || (shouldMove && kiteStyle)) {
                return ToolCallDecision(
                    tool = "game_sync",
                    arguments = buildJsonObject {
                        put("sessionId", sessionId)
                        put("clientYaw", AgentCombatHelper.aimYaw(pose, mob))
                        put("clientPitch", AgentCombatHelper.aimPitch(pose, mob))
                        put("reload", true)
                        put("backward", true)
                        if (criticalHp) put("strafeLeft", true)
                        put("deltaMs", COMBAT_DELTA_MS)
                    }.mapValues { it.value },
                )
            }
            if (inRoom && combatStyle != PolicyParams.COMBAT_KITE) {
                return ToolCallDecision(
                    tool = "game_sync",
                    arguments = buildJsonObject {
                        put("sessionId", sessionId)
                        put("clientYaw", AgentCombatHelper.aimYaw(pose, mob))
                        put("clientPitch", AgentCombatHelper.aimPitch(pose, mob))
                        put("reload", true)
                        put("deltaMs", COMBAT_DELTA_MS)
                    }.mapValues { it.value },
                )
            }
        }
        return AgentCombatHelper.reloadDecision(sessionId)
    }

    internal fun shouldPlantAndShoot(snapshot: GameSnapshot, mob: MobSnapshot): Boolean {
        if (!AgentCombatHelper.inActiveCombatRoom(snapshot)) return false
        return when (mob.kind) {
            MobKind.LLM_GUARD, MobKind.RANGED -> true
            MobKind.MELEE -> {
                val dist = AgentCombatHelper.distanceToMob(snapshot.player.pose, mob)
                dist > MELEE_KITE_RANGE
            }
        }
    }

    internal fun plantAndShoot(sessionId: String, pose: PlayerPose, mob: MobSnapshot): ToolCallDecision =
        ToolCallDecision(
            tool = "game_sync",
            arguments = buildJsonObject {
                put("sessionId", sessionId)
                put("clientYaw", AgentCombatHelper.aimYaw(pose, mob))
                put("clientPitch", AgentCombatHelper.aimPitch(pose, mob))
                put("attack", true)
                put("deltaMs", COMBAT_DELTA_MS)
            }.mapValues { it.value },
        )

    internal fun chaseAndShoot(sessionId: String, pose: PlayerPose, mob: MobSnapshot): ToolCallDecision =
        ToolCallDecision(
            tool = "game_sync",
            arguments = buildJsonObject {
                put("sessionId", sessionId)
                put("clientYaw", AgentCombatHelper.aimYaw(pose, mob))
                put("clientPitch", AgentCombatHelper.aimPitch(pose, mob))
                put("attack", true)
                put("forward", true)
                put("deltaMs", COMBAT_DELTA_MS)
            }.mapValues { it.value },
        )

    internal fun isCornerTrapped(snapshot: GameSnapshot): Boolean {
        val map = AgentDoorHelper.tileMap(snapshot)
        val cell = GridPos(
            floor(snapshot.player.pose.x).toInt(),
            floor(snapshot.player.pose.y).toInt(),
        )
        return PolicyFpsPathfinder.isCornerTrap(map, cell)
    }

    private const val COMBAT_DELTA_MS = 120
    private const val CRITICAL_HP = 5
    private const val CRITICAL_HP_RATIO = 0.2f
    private const val MELEE_BACKUP_RANGE = 4f
    private const val MELEE_KITE_RANGE = 2f
}
