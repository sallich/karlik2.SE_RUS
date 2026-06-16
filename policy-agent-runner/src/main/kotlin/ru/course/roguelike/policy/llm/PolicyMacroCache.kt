package ru.course.roguelike.policy.llm

import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.observation.FailureSignal
import ru.course.roguelike.policy.observation.PolicySituation
import java.util.concurrent.ConcurrentHashMap

/** In-memory cache of successful macro patches keyed by situation + failure signals. */
object PolicyMacroCache {
    private val cache = ConcurrentHashMap<String, AgentPolicy>()

    fun get(situation: PolicySituation, signals: Set<FailureSignal>): AgentPolicy? =
        cache[key(situation, signals)]

    fun put(situation: PolicySituation, signals: Set<FailureSignal>, policy: AgentPolicy) {
        cache[key(situation, signals)] = policy
    }

    fun clear() = cache.clear()

    private fun key(situation: PolicySituation, signals: Set<FailureSignal>): String =
        "${situation.name}:${signals.sortedBy { it.name }.joinToString(",") { it.name }}"
}
