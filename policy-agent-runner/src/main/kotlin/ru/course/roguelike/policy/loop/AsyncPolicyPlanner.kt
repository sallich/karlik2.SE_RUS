package ru.course.roguelike.policy.loop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.llm.PolicyGenResult
import ru.course.roguelike.policy.llm.PolicyGenerator
import ru.course.roguelike.policy.observation.PolicyObservation
import ru.course.roguelike.policy.observation.PolicySituation
import ru.course.roguelike.shared.dto.GameSnapshot
import java.util.concurrent.atomic.AtomicReference

/**
 * Async macro replans with a priority queue — reasons are not dropped while LLM is busy.
 */
class AsyncPolicyPlanner(
    private val generator: PolicyGenerator,
    private val scope: CoroutineScope,
    private val staleReplanMaxSteps: Int = 12,
) {
    private val log = LoggerFactory.getLogger(AsyncPolicyPlanner::class.java)
    private var inFlight: Deferred<PendingReplan>? = null
    private val completed = AtomicReference<PendingReplan?>(null)
    private val queue = ArrayDeque<ReplanReason>()

    val isBusy: Boolean get() = inFlight?.isActive == true

    fun tryStartReplan(
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
        reason: ReplanReason,
    ): Boolean {
        if (inFlight?.isActive == true) {
            enqueue(reason)
            return false
        }
        val next = dequeue() ?: reason
        return tryStart(next, snapshot, context, current)
    }

    private fun enqueue(reason: ReplanReason) {
        if (reason !in queue) {
            queue.addLast(reason)
            val sorted = queue.sortedBy { priority(it) }
            queue.clear()
            queue.addAll(sorted)
            log.debug("Queued replan reason={} queue={}", reason, queue.map { it.name })
        }
    }

    private fun dequeue(): ReplanReason? = if (queue.isEmpty()) null else queue.removeFirst()

    private fun tryStart(
        reason: ReplanReason,
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
    ): Boolean {
        log.info("Scheduling async policy generation reason={}", reason)
        val scheduledStep = context.stepIndex
        val scheduledSituation = PolicyObservation.observe(snapshot, context).situation
        inFlight = scope.async {
            val started = System.nanoTime()
            val result = runCatching {
                generator.replan(snapshot, context, current, reason)
            }.getOrElse { ex ->
                log.warn("Async policy generation failed reason={}: {}", reason, ex.message)
                throw ex
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            log.info("Async policy ready reason={} rules={} ms={}", reason, result.policy.rules.size, ms)
            PendingReplan(
                reason = reason,
                result = result,
                scheduledStep = scheduledStep,
                scheduledSituation = scheduledSituation,
            ).also { completed.set(it) }
        }
        return true
    }

    fun pollCompleted(context: PolicyContext): PendingReplan? {
        val pending = completed.getAndSet(null) ?: return null
        if (isStale(pending, context)) {
            log.info(
                "Stale replan discarded reason={} age={} was={} now={}",
                pending.reason,
                context.stepIndex - pending.scheduledStep,
                pending.scheduledSituation,
                context.lastObservation?.situation,
            )
            return null
        }
        if (queue.isNotEmpty()) {
            log.debug("Replan queue still has {}", queue.map { it.name })
        }
        return pending
    }

    suspend fun awaitInFlight() {
        inFlight?.await()
    }

    private fun isStale(pending: PendingReplan, context: PolicyContext): Boolean {
        val age = context.stepIndex - pending.scheduledStep
        val currentSituation = context.lastObservation?.situation
        return age > staleReplanMaxSteps &&
            currentSituation != null &&
            currentSituation != pending.scheduledSituation
    }
}

data class PendingReplan(
    val reason: ReplanReason,
    val result: PolicyGenResult,
    val scheduledStep: Int = 0,
    val scheduledSituation: PolicySituation? = null,
)

private fun priority(reason: ReplanReason): Int = when (reason) {
    ReplanReason.ACTION_ERROR -> 0
    ReplanReason.LOOP_ESCAPE -> 1
    ReplanReason.ROOM_TIMER_CHANGE -> 2
    ReplanReason.COMBAT_STALEMATE -> 3
    ReplanReason.DOOR_STUCK -> 4
    ReplanReason.STUCK -> 5
    ReplanReason.KEY_COLLECTED -> 6
    ReplanReason.OBJECTIVE_DONE -> 7
    ReplanReason.PHASE_CHANGE -> 8
    ReplanReason.NO_PROGRESS -> 9
    ReplanReason.INTERVAL -> 10
    ReplanReason.INITIAL -> 11
}
