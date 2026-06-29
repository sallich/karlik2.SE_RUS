package ru.course.roguelike.policy.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.course.roguelike.agent.OllamaChatOptions
import ru.course.roguelike.agent.OpenAiChatRequest
import ru.course.roguelike.agent.OpenAiChatResponse
import ru.course.roguelike.agent.OpenAiMessage
import ru.course.roguelike.agent.tracker.LlmHealthChecker
import ru.course.roguelike.agent.tracker.LlmHealthResponse
import ru.course.roguelike.policy.config.PolicyAgentConfig
import ru.course.roguelike.policy.dsl.AgentPolicy
import ru.course.roguelike.policy.dsl.DefaultPolicies
import ru.course.roguelike.policy.loop.PolicyContext
import ru.course.roguelike.policy.loop.ReplanReason
import ru.course.roguelike.policy.observation.PolicyObservation
import ru.course.roguelike.policy.observation.PolicyObservationResult
import ru.course.roguelike.shared.dto.GameSnapshot

interface PolicyGenerator {
    suspend fun initialPolicy(snapshot: GameSnapshot, context: PolicyContext): PolicyGenResult
    suspend fun replan(
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
        reason: ReplanReason,
    ): PolicyGenResult
}

data class PolicyGenResult(
    val policy: AgentPolicy,
    val source: String,
    val tokensApprox: Int = 0,
)

class OllamaPolicyGenerator(
    private val config: PolicyAgentConfig,
) : PolicyGenerator {
    private val log = LoggerFactory.getLogger(OllamaPolicyGenerator::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val primaryClient = ollamaClient(config.llmRequestTimeoutMs)
    private val fastClient = ollamaClient(config.llmFallbackTimeoutMs)

    override suspend fun initialPolicy(snapshot: GameSnapshot, context: PolicyContext): PolicyGenResult {
        if (config.initialTemplateOnly) {
            return llmUnavailableFallback(context, snapshot, "initial-template-skip-llm")
        }

        val health = LlmHealthChecker.check(config.agent)
        logOllamaHealth(health)
        if (config.warmUpLlm && health.reachable && health.modelAvailable) {
            warmUpModel(config.ollamaModel, primaryClient)
        }

        val user = PolicyPromptBuilder.initialUserMessage(snapshot, context)
        val retryTail = mutableListOf<OpenAiMessage>()
        val attempts = config.initialLlmAttempts
        repeat(attempts) { attempt ->
            val sampleSeed = context.llmSampleSeed xor attempt
            val roundTail = retryTail.toMutableList()
            log.info(
                "[policy-llm-initial] attempt {}/{} model={} timeoutMs={} retryTurns={}",
                attempt + 1,
                attempts,
                config.ollamaModel,
                config.llmRequestTimeoutMs,
                roundTail.size / 2,
            )
            val primary = generate(
                user, "initial", config.ollamaModel, primaryClient, current = null,
                temperature = config.llmInitialTemperature,
                sampleSeed = sampleSeed,
                retryTail = roundTail,
            )
            if (primary?.policy != null) {
                return primary.policy
            }
            appendRetryTurn(roundTail, primary?.failure, primary?.rawContent)

            val fallback = generate(
                user, "initial-fallback", config.ollamaFallbackModel, fastClient, current = null,
                temperature = config.llmInitialTemperature,
                sampleSeed = sampleSeed,
                retryTail = roundTail,
            )
            if (fallback?.policy != null) {
                return fallback.policy
            }
            appendRetryTurn(roundTail, fallback?.failure, fallback?.rawContent)

            retryTail.clear()
            retryTail.addAll(roundTail)
            if (attempt < attempts - 1) {
                delay(config.initialRetryDelayMs * (attempt + 1))
            }
        }

        if (config.requireLlm) {
            throw IllegalStateException(buildInitialFailureMessage(health, attempts))
        }
        return llmUnavailableFallback(context, snapshot, "initial-llm-unavailable")
    }

    override suspend fun replan(
        snapshot: GameSnapshot,
        context: PolicyContext,
        current: AgentPolicy,
        reason: ReplanReason,
    ): PolicyGenResult {
        val observation = context.lastObservation
            ?: PolicyObservation.observe(snapshot, context)
        if (config.macroCacheEnabled && reason != ReplanReason.LOOP_ESCAPE) {
            PolicyMacroCache.get(observation.situation, observation.failureSignals)?.let { cached ->
                log.info("[policy-llm-cache] hit situation={} signals={}", observation.situation, observation.failureSignals)
                return PolicyGenResult(cached, "macro-cache")
            }
        }
        val basePolicy = if (reason == ReplanReason.LOOP_ESCAPE) {
            PolicyMerger.cleanNavigationFallbacks(current)
        } else {
            current
        }
        val user = PolicyPromptBuilder.replanUserMessage(snapshot, context, basePolicy, reason)
        val retryTail = mutableListOf<OpenAiMessage>()
        val attempts = if (reason == ReplanReason.LOOP_ESCAPE) 3 else 2
        repeat(attempts) { attempt ->
            val sampleSeed = context.llmSampleSeed xor context.replanCount xor attempt
            val roundTail = retryTail.toMutableList()
            val primary = generate(
                user, "replan", config.ollamaModel, primaryClient, current = basePolicy,
                temperature = config.llmReplanTemperature,
                sampleSeed = sampleSeed,
                retryTail = roundTail,
            )
            if (primary?.policy != null) {
                return cacheReplanIfNeeded(primary.policy, observation, reason)
            }
            appendRetryTurn(roundTail, primary?.failure, primary?.rawContent)

            val fallback = generate(
                user, "replan-fallback", config.ollamaFallbackModel, fastClient, current = basePolicy,
                temperature = config.llmReplanTemperature,
                sampleSeed = sampleSeed,
                retryTail = roundTail,
            )
            if (fallback?.policy != null) {
                return cacheReplanIfNeeded(fallback.policy, observation, reason)
            }
            appendRetryTurn(roundTail, fallback?.failure, fallback?.rawContent)

            retryTail.clear()
            retryTail.addAll(roundTail)
            if (attempt < attempts - 1) {
                delay(750L * (attempt + 1))
            }
        }
        log.error(
            "[policy-llm-replan] all {} attempts failed reason={} — keeping current LLM policy (no code patch)",
            attempts,
            reason,
        )
        return PolicyGenResult(
            PolicyMerger.mergeWithBaseline(basePolicy),
            "llm-replan-failed-keep-current-${reason.name.lowercase()}",
        )
    }

    private suspend fun warmUpModel(model: String, client: HttpClient) {
        if (model.isBlank()) return
        log.info("[policy-llm-warmup] loading model={} (first inference may take minutes on CPU)", model)
        generate(
            userMessage = "Reply with exactly one word: ok",
            label = "warmup",
            model = model,
            client = client,
            current = null,
            temperature = 0f,
            sampleSeed = 1,
        )
    }

    private fun logOllamaHealth(health: LlmHealthResponse) {
        if (!health.reachable || !health.modelAvailable) {
            log.error("[policy-llm] Ollama health: reachable={} model={} hint={} error={}",
                health.reachable, health.modelAvailable, health.hint, health.error)
        } else {
            log.info("[policy-llm] Ollama OK at {} model={}", health.ollamaBaseUrl, health.configuredModel)
        }
    }

    private fun buildInitialFailureMessage(health: LlmHealthResponse, attempts: Int): String = buildString {
        append("Initial macro policy failed after $attempts attempts — Ollama did not return valid JSON. ")
        append("GET /health shows llm.reachable=${health.reachable} modelAvailable=${health.modelAvailable}. ")
        health.hint?.let { append(it).append(' ') }
        append("OLLAMA_BASE_URL=${config.ollamaBaseUrl} model=${config.ollamaModel}. ")
        append("Cold load of qwen2.5:3b on CPU can exceed 3min — set POLICY_LLM_REQUEST_TIMEOUT_MS=300000 ")
        append("and POLICY_INITIAL_LLM_ATTEMPTS=8. ")
        if (config.ollamaBaseUrl.contains("host.docker.internal")) {
            append("WSL/Docker: ensure Ollama runs on Windows host, or use OLLAMA_BASE_URL=http://ollama:11434 with docker compose ollama service.")
        }
    }

    private fun llmUnavailableFallback(
        context: PolicyContext,
        snapshot: GameSnapshot,
        label: String,
    ): PolicyGenResult {
        log.warn("[policy-llm-$label] LLM unavailable — minimal survival fallback (no LLM strategy)")
        val raw = HeuristicPolicyGenerator.llmUnavailablePolicy(snapshot, context, "llm-unavailable")
        return PolicyGenResult(PolicyMerger.mergeWithBaseline(raw), label)
    }

    private fun cacheReplanIfNeeded(
        gen: PolicyGenResult,
        observation: PolicyObservationResult,
        reason: ReplanReason,
    ): PolicyGenResult {
        if (config.macroCacheEnabled && gen.source.startsWith("ollama") && reason != ReplanReason.LOOP_ESCAPE) {
            PolicyMacroCache.put(
                observation.situation,
                observation.failureSignals,
                gen.policy.copy(objective = null),
            )
        }
        return gen
    }

    private data class GenerateAttempt(
        val policy: PolicyGenResult?,
        val failure: PolicyLlmParseFailure?,
        val rawContent: String?,
    )

    private fun appendRetryTurn(
        retryTail: MutableList<OpenAiMessage>,
        failure: PolicyLlmParseFailure?,
        rawContent: String?,
    ) {
        if (failure == null || rawContent.isNullOrBlank()) return
        retryTail.add(OpenAiMessage(role = "assistant", content = rawContent.take(2000)))
        retryTail.add(OpenAiMessage(role = "user", content = PolicyPromptBuilder.jsonCorrectionMessage(failure)))
        while (retryTail.size > MAX_RETRY_TURN_MESSAGES) {
            retryTail.removeAt(0)
            retryTail.removeAt(0)
        }
        log.info(
            "[policy-llm-retry] queued correction kind={} detail={}",
            failure.kind,
            failure.detail.take(120),
        )
    }

    private suspend fun generate(
        userMessage: String,
        label: String,
        model: String,
        client: HttpClient,
        current: AgentPolicy?,
        temperature: Float = 0.2f,
        sampleSeed: Int? = null,
        retryTail: List<OpenAiMessage> = emptyList(),
    ): GenerateAttempt? {
        if (model.isBlank()) return null
        val messages = buildList {
            add(OpenAiMessage(role = "system", content = PolicyPromptBuilder.systemPrompt()))
            add(OpenAiMessage(role = "user", content = userMessage))
            addAll(retryTail)
        }
        val request = OpenAiChatRequest(
            model = model,
            messages = messages,
            stream = false,
            options = OllamaChatOptions(
                numCtx = config.ollamaNumCtx,
                numPredict = config.ollamaNumPredict,
                temperature = temperature,
                seed = sampleSeed,
                topP = config.llmTopP,
            ),
            keepAlive = config.ollamaKeepAlive,
        )
        return runCatching {
            val started = System.nanoTime()
            val response = client.post("${config.ollamaBaseUrl.trimEnd('/')}/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<OpenAiChatResponse>()
            val content = response.choices.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                log.warn("[policy-llm-$label] empty response from model={}", model)
                return GenerateAttempt(
                    policy = null,
                    failure = PolicyLlmParseFailure(
                        kind = PolicyLlmParseFailure.Kind.EMPTY,
                        detail = "model returned empty content",
                        rawSnippet = "",
                    ),
                    rawContent = content,
                )
            }
            when (val parsed = PolicyLlmParser.parseAndMerge(content, current)) {
                is PolicyLlmParseResult.Ok -> {
                    if (current == null && parsed.policy.objective == null) {
                        val failure = PolicyLlmParseFailure(
                            kind = PolicyLlmParseFailure.Kind.MISSING_OBJECTIVE,
                            detail = "objective field is required for initial macro policy",
                            rawSnippet = content.trim().take(500),
                        )
                        log.warn("[policy-llm-$label] response missing objective (required for macro brain)")
                        return GenerateAttempt(null, failure, content)
                    }
                    val ms = (System.nanoTime() - started) / 1_000_000
                    log.info(
                        "[policy-llm-$label] model={} rules={} ms={} objective={} policy={}",
                        model,
                        parsed.policy.rules.size,
                        ms,
                        parsed.policy.objective?.let { "${it.kind}@${it.target}" },
                        PolicyJson.encode(parsed.policy).take(500),
                    )
                    GenerateAttempt(
                        policy = PolicyGenResult(
                            parsed.policy,
                            "ollama-$label-$model",
                            tokensApprox = content.length / 4,
                        ),
                        failure = null,
                        rawContent = content,
                    )
                }
                is PolicyLlmParseResult.Failed -> {
                    log.warn(
                        "[policy-llm-$label] {} from model={}: {}",
                        parsed.failure.kind.name.lowercase(),
                        model,
                        parsed.failure.detail,
                    )
                    GenerateAttempt(null, parsed.failure, content)
                }
            }
        }.onFailure { ex ->
            log.warn("[policy-llm-$label] model={} failed: {}", model, ex.message)
        }.getOrNull()
    }

    private fun ollamaClient(timeoutMs: Long): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMs
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = timeoutMs
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    private companion object {
        /** Up to 2 correction turns (assistant bad reply + user fix) kept in context. */
        const val MAX_RETRY_TURN_MESSAGES = 4
    }
}

object PolicyGeneratorFactory {
    fun create(config: PolicyAgentConfig): PolicyGenerator =
        when (config.llmProvider.lowercase()) {
            "ollama" -> OllamaPolicyGenerator(config)
            else -> HeuristicPolicyGenerator()
        }
}
