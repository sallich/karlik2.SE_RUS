package ru.course.roguelike.game.domain.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.course.roguelike.game.infrastructure.agent.AgentRunnerMobClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LLM-босс: решения запрашиваются асинхронно, игровой тик не блокируется.
 * Между ответами LLM используется [ShooterBehavior] (и кэш последнего intent).
 */
class LlmGuardBehavior(
    private val mobClient: AgentRunnerMobClient = AgentRunnerMobClient.fromEnvironment(),
) : MobBehavior {
    private val fallback = ShooterBehavior()
    private val log = LoggerFactory.getLogger(LlmGuardBehavior::class.java)

    @Volatile
    private var cachedIntent: MobIntent? = null

    @Volatile
    private var cachedAtMs: Long = 0

    @Volatile
    private var lastRefreshScheduledMs: Long = 0

    private val requestInFlight = AtomicBoolean(false)

    override fun decide(context: MobDecisionContext): MobIntent {
        val now = System.currentTimeMillis()
        val interval = decisionIntervalMs()

        cachedIntent?.takeIf { now - cachedAtMs < interval }?.let { return it }

        scheduleRefreshIfDue(context, now, interval)
        return fallback.decide(context)
    }

    private fun scheduleRefreshIfDue(context: MobDecisionContext, now: Long, interval: Long) {
        if (!requestInFlight.compareAndSet(false, true)) return
        if (now - lastRefreshScheduledMs < interval) {
            requestInFlight.set(false)
            return
        }
        lastRefreshScheduledMs = now

        llmScope.launch {
            try {
                val intent = mobClient.decide(context)
                if (intent != null) {
                    cachedIntent = intent
                    cachedAtMs = System.currentTimeMillis()
                    log.debug("LLM guard mob ${context.mob.id} intent=$intent")
                }
            } finally {
                requestInFlight.set(false)
            }
        }
    }

    companion object {
        private val llmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun decisionIntervalMs(): Long =
            System.getenv("LLM_GUARD_DECISION_INTERVAL_MS")?.toLongOrNull() ?: DEFAULT_DECISION_INTERVAL_MS

        private const val DEFAULT_DECISION_INTERVAL_MS = 6_000L
    }
}
