package ru.course.roguelike.client

import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

fun interactionHint(
    pose: PlayerPose?,
    sessionEnded: Boolean,
    tileMap: TileMap?,
    exitGate: GridPos?,
    keysCollected: Int,
    keysRequired: Int,
    keyPickups: List<KeySnapshot>,
): String? {
    if (pose == null || sessionEnded || tileMap == null) return null

    exitGateHint(pose, exitGate, keysCollected, keysRequired)?.let { return it }
    nearKeyHint(pose, keyPickups)?.let { return it }
    return exitTileHint(tileMap, pose, keysCollected, keysRequired)
}

private fun exitGateHint(
    pose: PlayerPose,
    exitGate: GridPos?,
    keysCollected: Int,
    keysRequired: Int,
): String? {
    val gate = exitGate ?: return null
    val onGate = floor(pose.x).toInt() == gate.x && floor(pose.y).toInt() == gate.y
    if (!onGate) return null
    return gateInteractionMessage(keysCollected, keysRequired)
}

private fun nearKeyHint(pose: PlayerPose, keyPickups: List<KeySnapshot>): String? {
    val nearKey = keyPickups.minByOrNull {
        hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble())
    } ?: return null
    if (hypot((nearKey.x - pose.x).toDouble(), (nearKey.y - pose.y).toDouble()) > 0.65) return null
    return "Press E — pick up golden key"
}

private fun exitTileHint(
    tileMap: TileMap,
    pose: PlayerPose,
    keysCollected: Int,
    keysRequired: Int,
): String? {
    if (tileMap.getTileAt(pose.x, pose.y) != TileType.EXIT_GATE) return null
    return gateInteractionMessage(keysCollected, keysRequired)
}

private fun gateInteractionMessage(keysCollected: Int, keysRequired: Int): String =
    if (keysCollected >= keysRequired) {
        "Press E — insert keys and open the exit gate"
    } else {
        "Exit gate: need all keys ($keysCollected / $keysRequired)"
    }
