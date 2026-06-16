package ru.course.roguelike.policy.planner

import ru.course.roguelike.policy.knowledge.PlayerKnowledgeLayer
import ru.course.roguelike.agent.llm.AgentPromptBuilder
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import kotlin.math.floor
import kotlin.math.hypot

object PolicyItemPlanner {
    fun navigateToItem(
        sessionId: String,
        snapshot: GameSnapshot,
        knowledge: PlayerKnowledgeLayer,
        strictFairPlay: Boolean = true,
    ): ToolCallDecision {
        val item = snapshot.items.minByOrNull {
            hypot(
                (it.x - snapshot.player.pose.x).toDouble(),
                (it.y - snapshot.player.pose.y).toDouble(),
            )
        } ?: return PolicyKeyHuntPlanner.plan(sessionId, snapshot, knowledge, strictFairPlay = strictFairPlay)

        val map = knowledge.navigableMap(snapshot, strictFairPlay)
        val cell = GridPos(floor(snapshot.player.pose.x).toInt(), floor(snapshot.player.pose.y).toInt())
        val goal = GridPos(floor(item.x).toInt(), floor(item.y).toInt())
        val path = PolicyFpsPathfinder.path(map, cell, goal, allowVertical = true)
        val next = if (path != null && path.size >= 2) {
            path[1]
        } else {
            goal
        }
        return PolicyFpsNavigation.syncStepTowardCell(sessionId, snapshot.player.pose, next, map)
    }

    fun equipWeapon(sessionId: String, snapshot: GameSnapshot): ToolCallDecision {
        val hotbar = snapshot.player.hotbar
        val slot = hotbar?.slots?.indexOfFirst { it != null }?.takeIf { it >= 0 } ?: 0
        val yaw = snapshot.player.pose.yaw
        return PolicySyncActions.sync(sessionId, yaw, forward = false, deltaMs = 50, hotbarSelect = slot + 1)
    }

    fun manageInventory(sessionId: String, snapshot: GameSnapshot): ToolCallDecision {
        val yaw = snapshot.player.pose.yaw
        val inv = snapshot.player.inventory
        if (inv != null && inv.items.isNotEmpty()) {
            return PolicySyncActions.sync(
                sessionId,
                yaw,
                forward = false,
                deltaMs = 50,
                inventoryDrop = true,
            )
        }
        return PolicySyncActions.sync(sessionId, yaw, forward = false, deltaMs = 50, inventoryCycle = true)
    }

    fun pickupNearby(sessionId: String, snapshot: GameSnapshot, knowledge: PlayerKnowledgeLayer): ToolCallDecision {
        val weapon = snapshot.items.firstOrNull {
            it.kind == ItemKind.WEAPON_PISTOL || it.kind == ItemKind.WEAPON_SHOTGUN
        }
        if (weapon != null) {
            val dist = hypot(
                (weapon.x - snapshot.player.pose.x).toDouble(),
                (weapon.y - snapshot.player.pose.y).toDouble(),
            )
            if (dist <= 1.5) {
                return AgentPromptBuilder.interactDecision(sessionId)
            }
        }
        return navigateToItem(sessionId, snapshot, knowledge)
    }
}
