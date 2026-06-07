package ru.course.roguelike.shared.model

/** Радиусы взаимодействия и подбора — единые для сервера и клиентских подсказок. */
object InteractionConstants {
    /** Ключи, оружие (E), выход — ручной interact. */
    const val INTERACT_RADIUS = 0.65f

    /** Патроны, аптечки, опыт — автоподбор при приближении. */
    const val AUTO_PICKUP_RADIUS = 0.6f
}
