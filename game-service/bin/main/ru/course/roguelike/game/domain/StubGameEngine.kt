package ru.course.roguelike.game.domain

import ru.course.roguelike.game.GameSessionResponse
import ru.course.roguelike.game.PlayerActionResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class StubGameEngine {
    private val sessions = ConcurrentHashMap<String, GameSessionResponse>()

    fun createSession(seed: Long?): GameSessionResponse {
        val resolvedSeed = seed ?: Random.nextLong()
        val sessionId = UUID.randomUUID().toString()
        val session = GameSessionResponse(
            sessionId = sessionId,
            seed = resolvedSeed,
            phase = "EXPLORATION",
            message = "Stub session created. Game engine will be implemented in later milestones.",
        )
        sessions[sessionId] = session
        return session
    }

    fun getSession(sessionId: String): GameSessionResponse? = sessions[sessionId]

    fun applyAction(sessionId: String, action: String): PlayerActionResponse? {
        val session = sessions[sessionId] ?: return null
        sessions[sessionId] = session.copy(
            message = "Stub accepted action: $action",
        )
        return PlayerActionResponse(
            accepted = true,
            message = "Action '$action' recorded (stub).",
        )
    }
}
