package ru.course.roguelike.game.domain.ai

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import ru.course.roguelike.game.infrastructure.agent.AgentRunnerMobClient

class LlmGuardBehavior(
    private val mobClient: AgentRunnerMobClient = AgentRunnerMobClient.fromEnvironment(),
) : MobBehavior {
    private val fallback = ShooterBehavior()
    private val log = LoggerFactory.getLogger(LlmGuardBehavior::class.java)
    private val lastDecisionMs = mutableMapOf<Long, Long>()

    override fun decide(context: MobDecisionContext): MobIntent {
        val now = System.currentTimeMillis()
        val last = lastDecisionMs[context.mob.id] ?: 0L
        if (now - last < DECISION_INTERVAL_MS) {
            return fallback.decide(context)
        }
        lastDecisionMs[context.mob.id] = now

        val intent = runBlocking {
            mobClient.decide(context)
        }
        if (intent != null) {
            log.debug("LLM guard mob ${context.mob.id} intent=$intent")
            return intent
        }
        return fallback.decide(context)
    }

    companion object {
        private const val DECISION_INTERVAL_MS = 2_000L
    }
}
