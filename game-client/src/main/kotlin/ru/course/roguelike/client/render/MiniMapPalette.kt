package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Color
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.ItemKind
import ru.course.roguelike.shared.model.MobKind
import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

internal object MiniMapPalette {
    val background = Color(0f, 0f, 0f, 0.6f)
    val floor = Color(0.16f, 0.18f, 0.22f, 0.9f)

    fun itemColor(kind: ItemKind): Color = when (kind) {
        ItemKind.HEALTH -> Color.SCARLET
        ItemKind.EXPERIENCE -> Color.LIME
        ItemKind.WEAPON_PISTOL -> Color.SKY
        ItemKind.WEAPON_SHOTGUN -> Color.FIREBRICK
        ItemKind.AMMO_PISTOL -> Color.SKY
        ItemKind.AMMO_SHOTGUN -> Color.ORANGE
    }

    fun mobColor(kind: MobKind): Color = when (kind) {
        MobKind.MELEE -> Color.GOLD
        MobKind.RANGED -> Color.SKY
        MobKind.LLM_GUARD -> Color.MAGENTA
    }

    /** Цвет метки приза над дверью (issue #24): ключ — золото, иначе цвет предмета. */
    fun markerColor(kind: ItemKind?): Color = kind?.let { itemColor(it) } ?: Color.GOLD

    fun cellColor(tile: TileType?, isHatchEntrance: Boolean = false): Color? = when (tile) {
        TileType.FLOOR -> floor
        TileType.WALL -> Color.DARK_GRAY
        TileType.COLUMN -> Color.GRAY
        TileType.LAVA -> Color.RED
        TileType.ELEVATOR -> Color.CYAN
        TileType.EXIT_GATE -> Color(0.2f, 0.85f, 0.35f, 1f)
        TileType.ROOM_DOOR -> Color(0.8f, 0.2f, 0.16f, 1f)
        TileType.ROOM_SEAL -> if (isHatchEntrance) Color.CYAN else Color(0.8f, 0.2f, 0.16f, 1f)
        else -> null
    }

    fun cellOf(worldX: Float, worldY: Float): GridPos =
        GridPos(floor(worldX).toInt(), floor(worldY).toInt())
}
