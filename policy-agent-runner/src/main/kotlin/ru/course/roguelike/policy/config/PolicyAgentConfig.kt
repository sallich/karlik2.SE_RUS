package ru.course.roguelike.policy.config

import ru.course.roguelike.agent.config.AgentConfig

/**
 * Extends shared agent MCP/LLM settings with macro-policy tuning.
 * No game-service URL — MCP only (TZ boundary).
 */
data class PolicyAgentConfig(
    val agent: AgentConfig,
    val maxToolCalls: Int,
    val stuckThreshold: Int,
    val replanEverySteps: Int,
    val stuckReplanCooldown: Int,
    val noProgressSteps: Int,
    val macroCacheEnabled: Boolean = false,
    val initialTemplateOnly: Boolean = false,
    val staleReplanMaxSteps: Int = 12,
    val combatStallSteps: Int = DEFAULT_COMBAT_STALL_STEPS,
    val llmInitialTemperature: Float = DEFAULT_LLM_INITIAL_TEMPERATURE,
    val llmReplanTemperature: Float = DEFAULT_LLM_REPLAN_TEMPERATURE,
    val llmTopP: Float = DEFAULT_LLM_TOP_P,
    val ollamaKeepAlive: String = "30m",
    val strictFairPlay: Boolean = true,
    /** When true (default for ollama), run fails if initial macro policy did not come from the LLM. */
    val requireLlm: Boolean = true,
    val initialLlmAttempts: Int = DEFAULT_INITIAL_LLM_ATTEMPTS,
    val initialRetryDelayMs: Long = DEFAULT_INITIAL_RETRY_DELAY_MS,
    val warmUpLlm: Boolean = true,
) {
    val llmProvider: String get() = agent.llmProvider
    val ollamaBaseUrl: String get() = agent.ollamaBaseUrl
    val ollamaModel: String get() = agent.ollamaModel
    val ollamaFallbackModel: String get() = agent.ollamaFallbackModel
    val llmRequestTimeoutMs: Long get() = agent.llmRequestTimeoutMs
    val llmFallbackTimeoutMs: Long get() = agent.llmFallbackTimeoutMs
    val ollamaNumCtx: Int get() = agent.ollamaNumCtx
    val ollamaNumPredict: Int get() = agent.ollamaNumPredict
    val mcpTransport: String get() = agent.mcpTransport

    companion object {
        fun fromEnvironment(): PolicyAgentConfig {
            val agent = AgentConfig.fromEnvironment().let { base ->
                val policyLlm = env("POLICY_LLM_PROVIDER")
                if (policyLlm.isNullOrBlank()) base else base.copy(llmProvider = policyLlm)
            }
            val llmProvider = agent.llmProvider.lowercase()
            val requireLlm = env("POLICY_REQUIRE_LLM")?.toBooleanStrictOrNull()
                ?: (llmProvider == "ollama")
            return PolicyAgentConfig(
                agent = agent,
                maxToolCalls = env("POLICY_MAX_TOOL_CALLS")?.toIntOrNull()
                    ?: env("AGENT_MAX_TOOL_CALLS")?.toIntOrNull()
                    ?: DEFAULT_MAX_TOOL_CALLS,
                stuckThreshold = env("POLICY_STUCK_THRESHOLD")?.toIntOrNull() ?: 3,
                replanEverySteps = env("POLICY_REPLAN_EVERY_STEPS")?.toIntOrNull() ?: 15,
                stuckReplanCooldown = env("POLICY_STUCK_REPLAN_COOLDOWN")?.toIntOrNull() ?: 8,
                noProgressSteps = env("POLICY_NO_PROGRESS_STEPS")?.toIntOrNull() ?: 15,
                macroCacheEnabled = env("POLICY_MACRO_CACHE")?.toBooleanStrictOrNull() ?: false,
                initialTemplateOnly = env("POLICY_INITIAL_TEMPLATE_ONLY")?.toBooleanStrictOrNull() ?: false,
                staleReplanMaxSteps = env("POLICY_STALE_REPLAN_STEPS")?.toIntOrNull() ?: 12,
                combatStallSteps = env("POLICY_COMBAT_STALL_STEPS")?.toIntOrNull() ?: 35,
                llmInitialTemperature = env("POLICY_LLM_INITIAL_TEMPERATURE")?.toFloatOrNull()
                    ?: DEFAULT_LLM_INITIAL_TEMPERATURE,
                llmReplanTemperature = env("POLICY_LLM_REPLAN_TEMPERATURE")?.toFloatOrNull()
                    ?: DEFAULT_LLM_REPLAN_TEMPERATURE,
                llmTopP = env("POLICY_LLM_TOP_P")?.toFloatOrNull() ?: DEFAULT_LLM_TOP_P,
                ollamaKeepAlive = env("POLICY_OLLAMA_KEEP_ALIVE") ?: "30m",
                strictFairPlay = env("POLICY_STRICT_FAIR_PLAY")?.toBooleanStrictOrNull() ?: true,
                requireLlm = requireLlm,
                initialLlmAttempts = env("POLICY_INITIAL_LLM_ATTEMPTS")?.toIntOrNull()
                    ?: DEFAULT_INITIAL_LLM_ATTEMPTS,
                initialRetryDelayMs = env("POLICY_INITIAL_RETRY_DELAY_MS")?.toLongOrNull()
                    ?: DEFAULT_INITIAL_RETRY_DELAY_MS,
                warmUpLlm = env("POLICY_LLM_WARMUP")?.toBooleanStrictOrNull() ?: true,
            )
        }

        const val DEFAULT_MAX_TOOL_CALLS = 20_000
        const val DEFAULT_COMBAT_STALL_STEPS = 35
        const val DEFAULT_LLM_INITIAL_TEMPERATURE = 0.75f
        const val DEFAULT_LLM_REPLAN_TEMPERATURE = 0.85f
        const val DEFAULT_LLM_TOP_P = 0.92f
        const val DEFAULT_INITIAL_LLM_ATTEMPTS = 5
        const val DEFAULT_INITIAL_RETRY_DELAY_MS = 3_000L

        private fun env(name: String): String? =
            System.getenv(name) ?: System.getProperty(name)
    }
}
