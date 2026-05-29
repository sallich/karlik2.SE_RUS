package ru.course.roguelike.shared.model

import kotlinx.serialization.Serializable

/**
 * Тип тайла. Свойства задаются явно, чтобы новые типы (колонны, лава)
 * автоматически корректно обрабатывались системами движения и отрисовки,
 * которые опираются на [walkable] / [blocksVision] / [damaging].
 */
@Serializable
enum class TileType(
    /** Может ли герой находиться на этом тайле (лава проходима, но наносит урон). */
    val walkable: Boolean,
    /** Перекрывает ли тайл линию обзора (стены и колонны рендерятся как сплошные). */
    val blocksVision: Boolean,
    /** Наносит ли тайл урон герою, пока тот на нём стоит. */
    val damaging: Boolean,
) {
    FLOOR(walkable = true, blocksVision = false, damaging = false),
    WALL(walkable = false, blocksVision = true, damaging = false),

    /** Колонна — препятствие для маневрирования: блокирует и движение, и обзор. */
    COLUMN(walkable = false, blocksVision = true, damaging = false),

    /** Сегмент лавы — проходим, но наносит урон, пока герой в нём. */
    LAVA(walkable = true, blocksVision = false, damaging = true),

    /** Лифт — проходимый тайл-переход между уровнями (двухуровневая локация). */
    ELEVATOR(walkable = true, blocksVision = false, damaging = false),
}
