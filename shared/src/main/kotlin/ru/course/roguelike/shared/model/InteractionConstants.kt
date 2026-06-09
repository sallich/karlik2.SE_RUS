package ru.course.roguelike.shared.model

/** Радиусы взаимодействия и подбора — единые для сервера и клиентских подсказок. */
object InteractionConstants {
    /** Ключи, оружие (E), выход — ручной interact. */
    const val INTERACT_RADIUS = 0.65f

    /** Дверь комнаты — чуть дальше, т.к. игрок стоит на соседнем тайле коридора. */
    const val DOOR_INTERACT_RADIUS = 1.35f

    /** Патроны, аптечки, опыт — автоподбор при приближении. */
    const val AUTO_PICKUP_RADIUS = 0.6f
}
