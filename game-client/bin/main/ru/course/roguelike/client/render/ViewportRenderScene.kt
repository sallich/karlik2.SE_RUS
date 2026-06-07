package ru.course.roguelike.client.render

import ru.course.roguelike.shared.dto.ItemSnapshot
import ru.course.roguelike.shared.dto.KeySnapshot
import ru.course.roguelike.shared.dto.MobSnapshot
import ru.course.roguelike.shared.dto.ProjectileSnapshot
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.PlayerPose

data class ViewportRenderScene(
    val map: TileMap,
    val pose: PlayerPose,
    val floorLevel: Int = 0,
    val mobs: List<MobSnapshot> = emptyList(),
    val projectiles: List<ProjectileSnapshot> = emptyList(),
    val keyPickups: List<KeySnapshot> = emptyList(),
    val items: List<ItemSnapshot> = emptyList(),
    val agentPose: PlayerPose? = null,
)
