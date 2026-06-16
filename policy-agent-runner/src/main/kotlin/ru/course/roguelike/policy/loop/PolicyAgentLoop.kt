package ru.course.roguelike.policy.loop

import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.AgentRunResponse
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.McpClientFactory
import ru.course.roguelike.agent.tracker.AgentLiveTracker
import ru.course.roguelike.policy.PolicyRunRequest
import ru.course.roguelike.policy.PolicyRunResponse
import ru.course.roguelike.policy.config.PolicyAgentConfig
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.PolicyInterpreter
import ru.course.roguelike.policy.llm.PolicyGenerator
import ru.course.roguelike.policy.llm.PolicyGeneratorFactory
import ru.course.roguelike.policy.llm.PolicyJson
import ru.course.roguelike.policy.llm.PolicyMerger
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.SessionPhase

/**
 * Macro/micro agent loop:
 * - Macro (LLM): initial policy synchronously, replans asynchronously
 * - Micro (interpreter): each step → one MCP tool call using the last applied policy
 */
class PolicyAgentLoop(
    private val config: PolicyAgentConfig,
    private val mcpFactory: (PolicyAgentConfig) -> McpClient = { McpClientFactory.create(it.agent) },
    private val generatorFactory: (PolicyAgentConfig) -> PolicyGenerator = PolicyGeneratorFactory::create,
) {
    private val log = LoggerFactory.getLogger(PolicyAgentLoop::class.java)

    suspend fun run(request: PolicyRunRequest): PolicyRunResponse {
        val budget = minOf(request.maxSteps, config.maxToolCalls)
        log.info("Policy run budget={} (maxSteps={}, POLICY_MAX_TOOL_CALLS={})", budget, request.maxSteps, config.maxToolCalls)
        val mcp = mcpFactory(config)
        val generator = generatorFactory(config)
        val toolLog = mutableListOf<String>()

        return try {
            runWithMcp(mcp, generator, request, budget, toolLog, replanEnabled = true)
        } catch (ex: IllegalStateException) {
            log.error("Policy agent run failed", ex)
            AgentLiveTracker.onRunFailed(ex.message ?: "policy agent failed")
            failed(ex.message ?: "policy agent failed", toolLog)
        } finally {
            mcp.close()
        }
    }

    /** Test hook: skip LLM macro, run micro loop with a fixed policy (no replans). */
    internal suspend fun runWithPolicy(
        request: PolicyRunRequest,
        fixedPolicy: AgentPolicy,
        mergeWithBaseline: Boolean = false,
    ): PolicyRunResponse {
        val budget = minOf(request.maxSteps, config.maxToolCalls)
        val mcp = mcpFactory(config)
        val toolLog = mutableListOf<String>()
        val policy = if (mergeWithBaseline) {
            PolicyMerger.mergeWithBaseline(fixedPolicy)
        } else {
            fixedPolicy
        }
        return try {
            runWithMcp(
                mcp = mcp,
                generator = PolicyGeneratorFactory.create(config),
                request = request,
                budget = budget,
                toolLog = toolLog,
                replanEnabled = false,
                initialPolicy = policy,
            )
        } finally {
            mcp.close()
        }
    }

    private suspend fun runWithMcp(
        mcp: McpClient,
        generator: PolicyGenerator,
        request: PolicyRunRequest,
        budget: Int,
        toolLog: MutableList<String>,
        replanEnabled: Boolean = true,
        initialPolicy: AgentPolicy? = null,
    ): PolicyRunResponse = coroutineScope {
        var policyTokens = 0
        val sessionId = request.sessionId ?: createSession(mcp, request.seed, toolLog)
        if (sessionId == null) {
            AgentLiveTracker.onRunFailed("Failed to create session via MCP")
            return@coroutineScope failed("Failed to create session via MCP", toolLog)
        }

        AgentLiveTracker.onRunStart(sessionId, request.seed)

        val context = PolicyContext(
            stuckThreshold = config.stuckThreshold,
            replanEverySteps = config.replanEverySteps,
            stuckReplanCooldown = config.stuckReplanCooldown,
            noProgressSteps = config.noProgressSteps,
            strictFairPlay = config.strictFairPlay,
            combatStallSteps = config.combatStallSteps,
        )
        context.llmProvider = config.llmProvider
        val executor = PolicyMcpExecutor(mcp, sessionId, toolLog)
        val asyncPlanner = if (replanEnabled) AsyncPolicyPlanner(generator, this, config.staleReplanMaxSteps) else null

        val initialSnapshot = executor.observe() ?: run {
            AgentLiveTracker.onRunFailed("Failed to observe session")
            return@coroutineScope failed("Failed to observe session", toolLog)
        }
        var currentSnapshot: GameSnapshot = initialSnapshot
        context.recordSnapshot(currentSnapshot, "start")
        context.initRunVariation(currentSnapshot.seed)
        context.seedFromSnapshot(currentSnapshot)
        AgentLiveTracker.onObserve(0, currentSnapshot)

        var policy = if (initialPolicy != null) {
            context.currentPolicy = initialPolicy
            context.setActiveObjective(initialPolicy.objective)
            context.markReplanned(ReplanReason.INITIAL)
            initialPolicy
        } else {
            awaitInitialPolicy(generator, initialSnapshot, context).also {
                policyTokens += it.tokensAdded
            }.policy
        }

        var steps = 0
        var finalPhase = currentSnapshot.phase

        while (steps < budget && !isTerminalPhase(currentSnapshot.phase)) {
            if (replanEnabled && asyncPlanner != null) {
                applyCompletedPolicy(asyncPlanner, context)?.let { applied ->
                    policy = applied.policy
                    policyTokens += applied.tokensAdded
                }

                handleReplan(asyncPlanner, generator, executor, currentSnapshot, context, policy).let { (next, tokens) ->
                    policy = next
                    policyTokens += tokens
                }
            }

            val observed = executor.observe() ?: currentSnapshot
            val stepResult = executeMicroStep(executor, context, sessionId, observed, policy, steps)
            currentSnapshot = stepResult.snapshot
            finalPhase = currentSnapshot.phase
            steps++
        }

        if (replanEnabled && asyncPlanner != null) {
            asyncPlanner.awaitInFlight()
            applyCompletedPolicy(asyncPlanner, context)?.let { applied ->
                policy = applied.policy
                policyTokens += applied.tokensAdded
            }
        }

        val response = RunOutcome(
            sessionId = sessionId,
            budget = budget,
            steps = steps,
            finalPhase = finalPhase,
            toolLog = toolLog,
            context = context,
            policy = policy,
            policyTokens = policyTokens,
        ).toResponse()
        AgentLiveTracker.onRunFinish(response.toAgentRunResponse())
        response
    }

    private suspend fun awaitInitialPolicy(
        generator: PolicyGenerator,
        snapshot: GameSnapshot,
        context: PolicyContext,
    ): AppliedPolicy {
        log.info("Waiting for initial LLM policy (first decision)...")
        val gen = generator.initialPolicy(snapshot, context)
        if (config.requireLlm && !gen.source.startsWith("ollama")) {
            throw IllegalStateException(
                "Macro brain unavailable (source=${gen.source}). " +
                    "Check GET http://localhost:8083/health — field llm.reachable and llm.hint. " +
                    "Set POLICY_REQUIRE_LLM=false only for offline tests.",
            )
        }
        val merged = PolicyMerger.mergeWithBaseline(gen.policy)
        context.currentPolicy = merged
        context.setActiveObjective(merged.objective)
        context.markReplanned(ReplanReason.INITIAL)
        context.lastPolicySource = gen.source
        context.recordMacroDecision(ReplanReason.INITIAL, gen.source, merged)
        AgentLiveTracker.onReplan(context.replanCount, ReplanReason.INITIAL.name)
        AgentLiveTracker.onMacroDecision(context.macroDecisions.last().trackerLine())
        log.info(
            "Initial policy ready rules={} source={} json={}",
            merged.rules.size,
            gen.source,
            PolicyJson.encode(merged).take(500),
        )
        return AppliedPolicy(merged, gen.tokensApprox)
    }

    private fun applyCompletedPolicy(
        asyncPlanner: AsyncPolicyPlanner,
        context: PolicyContext,
    ): AppliedPolicy? {
        val pending = asyncPlanner.pollCompleted(context) ?: return null
        val merged = PolicyMerger.mergeWithBaseline(pending.result.policy)
        context.currentPolicy = merged
        context.setActiveObjective(merged.objective)
        context.markReplanned(pending.reason)
        context.lastPolicySource = pending.result.source
        context.recordMacroDecision(pending.reason, pending.result.source, merged)
        AgentLiveTracker.onReplan(context.replanCount, pending.reason.name)
        AgentLiveTracker.onMacroDecision(context.macroDecisions.last().trackerLine())
        log.info(
            "Policy applied reason={} rules={} source={} json={}",
            pending.reason,
            merged.rules.size,
            pending.result.source,
            PolicyJson.encode(merged).take(400),
        )
        return AppliedPolicy(merged, pending.result.tokensApprox)
    }

    private suspend fun handleReplan(
        asyncPlanner: AsyncPolicyPlanner,
        generator: PolicyGenerator,
        executor: PolicyMcpExecutor,
        snapshot: GameSnapshot,
        context: PolicyContext,
        policy: AgentPolicy,
    ): Pair<AgentPolicy, Int> {
        val reason = context.shouldReplan(snapshot, isInitial = false) ?: return policy to 0
        if (context.isUrgentReplan(reason)) {
            context.onReplanScheduled(reason)
            log.info("Sync loop-escape replan at step={} streak={}", context.stepIndex, context.actionLoopStreak)
            val fresh = executor.observe() ?: snapshot
            context.recordSnapshot(fresh, "sync-replan")
            val gen = generator.replan(fresh, context, policy, reason)
            val merged = PolicyMerger.mergeWithBaseline(gen.policy)
            context.currentPolicy = merged
            context.setActiveObjective(merged.objective)
            context.markReplanned(reason)
            context.onSyncReplanApplied(reason)
            context.lastPolicySource = gen.source
            context.recordMacroDecision(reason, gen.source, merged)
            AgentLiveTracker.onReplan(context.replanCount, reason.name)
            AgentLiveTracker.onMacroDecision(context.macroDecisions.last().trackerLine())
            log.info(
                "Sync policy applied reason={} rules={} source={} json={}",
                reason,
                merged.rules.size,
                gen.source,
                PolicyJson.encode(merged).take(400),
            )
            return merged to gen.tokensApprox
        }
        if (asyncPlanner.tryStartReplan(snapshot, context, policy, reason)) {
            context.onReplanScheduled(reason)
            log.debug("Async replan scheduled at step={} reason={}", context.stepIndex, reason)
        }
        return policy to 0
    }

    private suspend fun executeMicroStep(
        executor: PolicyMcpExecutor,
        context: PolicyContext,
        sessionId: String,
        snapshot: GameSnapshot,
        policy: AgentPolicy,
        steps: Int,
    ): MicroStepResult {
        context.recordSnapshot(snapshot, "pre-step")
        AgentLiveTracker.onObserve(steps, snapshot)

        val calibrated = if (context.seekRoomExit) {
            snapshot
        } else {
            executor.calibrateIfNeeded(snapshot)
        }
        val interpreted = PolicyInterpreter.interpret(policy, calibrated, sessionId, context)
        context.recordActionTrace(interpreted.condition, calibrated)
        val (debugLines, highlights) = ru.course.roguelike.policy.tracker.PolicyTrackerDebug.build(
            calibrated,
            context,
            interpreted.condition,
        )
        AgentLiveTracker.onPolicyDebug(debugLines, highlights)
        log.debug(
            "step={} condition={} tool={}",
            steps,
            interpreted.condition,
            interpreted.decision.tool,
        )

        val before = calibrated
        val outcome = executor.execute(interpreted.decision)
        val after = outcome.snapshot ?: before
        context.updateAfterStep(before, after, interpreted.decision, outcome.isError)

        val detail = actionLabel(interpreted.decision)
        AgentLiveTracker.onAction(
            step = steps,
            tool = interpreted.decision.tool,
            detail = detail,
            source = "policy:${interpreted.condition}",
            error = outcome.isError,
        )
        if (outcome.isError) {
            log.warn("MCP error at step={}: {}", steps + 1, outcome.errorText)
        }

        return MicroStepResult(after)
    }

    private fun failed(message: String, log: List<String>) = PolicyRunResponse(
        status = "FAILED",
        message = message,
        toolCallLog = log,
    )

    private data class AppliedPolicy(val policy: AgentPolicy, val tokensAdded: Int)

    private data class MicroStepResult(val snapshot: GameSnapshot)

    private data class RunOutcome(
        val sessionId: String,
        val budget: Int,
        val steps: Int,
        val finalPhase: String,
        val toolLog: List<String>,
        val context: PolicyContext,
        val policy: AgentPolicy,
        val policyTokens: Int,
    ) {
        fun toResponse(): PolicyRunResponse {
            val success = finalPhase == SessionPhase.LEVEL_COMPLETE.name
            return PolicyRunResponse(
                status = if (success) "COMPLETE" else "STOPPED",
                message = if (success) {
                    "Level completed via policy agent"
                } else {
                    "Stopped at phase $finalPhase"
                },
                stepsUsed = steps,
                stepsPlanned = budget,
                sessionId = sessionId,
                finalPhase = finalPhase,
                success = success,
                toolCallLog = toolLog,
                replanCount = context.replanCount,
                replanLog = context.replanLog.toList(),
                macroDecisions = context.macroDecisions.toList(),
                finalPolicy = policy,
                policyTokensApprox = policyTokens,
                stuckEvents = context.stuckEventCount,
                knowledgeCellsKnown = context.knowledge.knownCells.size,
            )
        }
    }
}

private fun PolicyRunResponse.toAgentRunResponse() = AgentRunResponse(
    status = status,
    message = message,
    stepsUsed = stepsUsed,
    stepsPlanned = stepsPlanned,
    sessionId = sessionId,
    finalPhase = finalPhase,
    success = success,
    toolCallLog = toolCallLog,
)
