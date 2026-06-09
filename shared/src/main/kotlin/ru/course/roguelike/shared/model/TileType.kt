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

    /** Ворота выхода в комнате босса — сюда нужно принести все ключи и нажать E. */
    EXIT_GATE(walkable = true, blocksVision = false, damaging = false),

    /**
     * Запертая дверь комнаты (issue #24): появляется в дверных проёмах, пока герой
     * заперт внутри в бою. Невидимый барьер коллизии — блокирует движение героя
     * (но не мобов, см. [ru.course.roguelike.shared.engine.EntityCollision]) и не
     * перекрывает обзор: сама дверь рисуется billboard-панелью на клиенте, а сквозь
     * проём видно комнату. После зачистки снова становится полом.
     */
    DOOR_LOCKED(walkable = false, blocksVision = false, damaging = false),
}
