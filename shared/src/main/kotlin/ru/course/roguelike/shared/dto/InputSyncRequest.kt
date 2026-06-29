package ru.course.roguelike.shared.dto

import kotlinx.serialization.Serializable

/** Пакет ввода за интервал (~50 ms при 20 Hz), не один шаг сетки. */
@Serializable
data class InputSyncRequest(
    val forward: Boolean = false,
    val backward: Boolean = false,
    val strafeLeft: Boolean = false,
    val strafeRight: Boolean = false,
    val turnLeft: Boolean = false,
    val turnRight: Boolean = false,
    val lookUp: Boolean = false,
    val lookDown: Boolean = false,
    val yawDelta: Float = 0f,
    val pitchDelta: Float = 0f,
    val deltaMs: Int = 50,
    val clientYaw: Float? = null,
    val clientPitch: Float? = null,
    val attack: Boolean = false,
    val interact: Boolean = false,
    /** Экипировать оружие из слота hotbar (1 или 2). */
    val hotbarSelect: Int? = null,
    /** Tab+1/2: назначить следующее оружие из инвентаря в слот hotbar. */
    val hotbarAssign: Int? = null,
    /** Перезарядка из инвентаря под текущее оружие (F, инвентарь закрыт). */
    val reload: Boolean = false,
    /** Tab: Q — переключить выбранный предмет в сетке. */
    val inventoryCycle: Boolean = false,
    /** Tab: F — выбросить выбранный предмет. */
    val inventoryDrop: Boolean = false,
    val jump: Boolean = false,
    val actor: String = "player",
)
