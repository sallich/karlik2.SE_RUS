package ru.course.roguelike.game.domain.event

/**
 * Observer: подписчики на доменные события (метрики, реплей, логи).
 */
class GameEventBus {
    private val listeners = mutableListOf<GameEventListener>()

    fun subscribe(listener: GameEventListener) {
        listeners += listener
    }

    fun publish(events: List<GameEvent>) {
        if (events.isEmpty()) return
        listeners.forEach { listener ->
            events.forEach { event -> listener.onEvent(event) }
        }
    }
}

fun interface GameEventListener {
    fun onEvent(event: GameEvent)
}
