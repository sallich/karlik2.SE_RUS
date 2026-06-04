package ru.course.roguelike.client

import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

private const val PICKUP_HINT_RADIUS = 0.65

/** Прогресс по ключам, нужный для подсказок у ворот выхода. */
data class KeyProgress(val collected: Int, val required: Int)

fun interactionHint(
    pose: PlayerPose?,
    sessionEnded: Boolean,
    tileMap: TileMap?,
    exitGate: GridPos?,
    keys: KeyProgress,
    keyPickups: List<KeySnapshot>,
    items: List<ItemSnapshot> = emptyList(),
): String? {
    if (pose == null || sessionEnded || tileMap == null) return null

    return exitGateHint(pose, exitGate, keys)
        ?: nearKeyHint(pose, keyPickups)
        ?: nearWeaponHint(pose, items)
        ?: exitTileHint(tileMap, pose, keys)
}

private fun exitGateHint(pose: PlayerPose, exitGate: GridPos?, keys: KeyProgress): String? {
    val gate = exitGate ?: return null
    val onGate = floor(pose.x).toInt() == gate.x && floor(pose.y).toInt() == gate.y
    if (!onGate) return null
    return gateInteractionMessage(keys)
}

private fun nearKeyHint(pose: PlayerPose, keyPickups: List<KeySnapshot>): String? {
    val nearKey = keyPickups.minByOrNull {
        hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble())
    } ?: return null
    if (hypot((nearKey.x - pose.x).toDouble(), (nearKey.y - pose.y).toDouble()) > PICKUP_HINT_RADIUS) return null
    return "Press E — pick up golden key"
}

private fun nearWeaponHint(pose: PlayerPose, items: List<ItemSnapshot>): String? {
    val nearWeapon = items
        .filter { it.kind == ItemKind.WEAPON }
        .minByOrNull { hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble()) }
        ?: return null
    if (hypot((nearWeapon.x - pose.x).toDouble(), (nearWeapon.y - pose.y).toDouble()) > PICKUP_HINT_RADIUS) {
        return null
    }
    return "Press E — pick up weapon"
}

private fun exitTileHint(tileMap: TileMap, pose: PlayerPose, keys: KeyProgress): String? {
    if (tileMap.getTileAt(pose.x, pose.y) != TileType.EXIT_GATE) return null
    return gateInteractionMessage(keys)
}

private fun gateInteractionMessage(keys: KeyProgress): String =
    if (keys.collected >= keys.required) {
        "Press E — insert keys and open the exit gate"
    } else {
        "Exit gate: need all keys (${keys.collected} / ${keys.required})"
    }
