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
    /** Поворот мышью (радианы), синхронизируется с сервером. */
    val yawDelta: Float = 0f,
    val pitchDelta: Float = 0f,
    val deltaMs: Int = 50,
    /**
     * Абсолютный взгляд клиента на конец интервала (для sync).
     * Сервер интерполирует yaw/pitch и считает W по той же оси, что и камера.
     */
    val clientYaw: Float? = null,
    val clientPitch: Float? = null,
    val attack: Boolean = false,
    /** Взаимодействие: подобрать ключ, открыть ворота выхода. */
    val interact: Boolean = false,
    /** "player" (default) или "agent" — кто выполняет sync. */
    val actor: String = "player",
)
