package ru.course.roguelike.client

import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor
import kotlin.math.hypot

private const val PICKUP_HINT_RADIUS = 0.65

data class KeyProgress(val collected: Int, val required: Int)

fun interactionHint(
    pose: PlayerPose?,
    sessionEnded: Boolean,
    tileMap: TileMap?,
    exitGate: GridPos?,
    keys: KeyProgress,
    keyPickups: List<ru.course.roguelike.shared.dto.KeySnapshot>,
    items: List<ru.course.roguelike.shared.dto.ItemSnapshot> = emptyList(),
): String? {
    if (pose == null || sessionEnded || tileMap == null) return null
    return exitGateHint(pose, exitGate, keys)
        ?: nearKeyHint(pose, keyPickups)
        ?: nearWeaponHint(pose, items)
        ?: exitTileHint(tileMap, pose, keys)
}

private fun exitGateHint(pose: PlayerPose, exitGate: GridPos?, keys: KeyProgress): String? {
    val gate = exitGate ?: return null
    if (floor(pose.x).toInt() != gate.x || floor(pose.y).toInt() != gate.y) return null
    return if (keys.collected >= keys.required) "E — открыть выход" else "Нужны ключи ${keys.collected}/${keys.required}"
}

private fun nearKeyHint(pose: PlayerPose, keyPickups: List<ru.course.roguelike.shared.dto.KeySnapshot>): String? {
    val near = keyPickups.minByOrNull { hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble()) } ?: return null
    if (hypot((near.x - pose.x).toDouble(), (near.y - pose.y).toDouble()) > PICKUP_HINT_RADIUS) return null
    return "E — ключ"
}

private fun nearWeaponHint(pose: PlayerPose, items: List<ru.course.roguelike.shared.dto.ItemSnapshot>): String? {
    val near = items
        .filter { it.kind == ItemKind.WEAPON_PISTOL || it.kind == ItemKind.WEAPON_SHOTGUN }
        .minByOrNull { hypot((it.x - pose.x).toDouble(), (it.y - pose.y).toDouble()) }
        ?: return null
    if (hypot((near.x - pose.x).toDouble(), (near.y - pose.y).toDouble()) > PICKUP_HINT_RADIUS) return null
    return when (near.kind) {
        ItemKind.WEAPON_SHOTGUN -> "E — дробовик"
        ItemKind.WEAPON_PISTOL -> "E — пистолет"
        else -> null
    }
}

private fun exitTileHint(tileMap: TileMap, pose: PlayerPose, keys: KeyProgress): String? {
    if (tileMap.getTileAt(pose.x, pose.y) != TileType.EXIT_GATE) return null
    return if (keys.collected >= keys.required) "E — открыть выход" else "Нужны ключи"
}
