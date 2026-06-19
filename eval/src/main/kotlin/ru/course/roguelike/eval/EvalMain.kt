package ru.course.roguelike.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.course.roguelike.agent.AgentRunRequest
import ru.course.roguelike.agent.config.AgentConfig
import ru.course.roguelike.agent.loop.AgentLoop
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.McpToolResult
import ru.course.roguelike.game.application.GameEngine
import ru.course.roguelike.mcp.client.GameSessionPort
import ru.course.roguelike.mcp.protocol.McpToolRegistry
import ru.course.roguelike.policy.PolicyRunRequest
import ru.course.roguelike.policy.config.PolicyAgentConfig
import ru.course.roguelike.policy.loop.PolicyAgentLoop
import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.dto.InputSyncRequest
import ru.course.roguelike.shared.dto.PlayerActionResponse
import ru.course.roguelike.shared.mcp.McpTool
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeText
import kotlinx.serialization.json.JsonElement

/**
 * In-process eval (no Docker): heuristic baseline, одинаковые сиды, N прогонов на агента.
 *
 * Usage: ./gradlew :eval:run --args="--runs 5 --seeds 41,42,43,44,45"
 */
fun main(args: Array<String>) = runBlocking {
    val options = EvalOptions.parse(args)
    if (options.fromLog != null) {
        regenerateFromLog(options.fromLog)
        return@runBlocking
    }
    System.setProperty("SKIP_MOBS", "true")
    System.setProperty("SKIP_LLM_MOB", "true")

    val root = Path.of(".").toAbsolutePath().normalize()
    val logsDir = root.resolve("eval/logs")
    Files.createDirectories(logsDir)
    val logFile = logsDir.resolve("run-${System.currentTimeMillis()}.jsonl")

    val stack = EvalStack.start()
    val stepMcp = EvalMcpClient(stack.registry)
    val policyMcp = EvalMcpClient(stack.registry)

    val records = mutableListOf<EvalRecord>()
    for (seed in options.seeds) {
        repeat(options.runsPerSeed) { runIndex ->
            records += runStepAgent(stepMcp, seed, runIndex, options.maxStepsStep)
            records += runPolicyAgent(policyMcp, seed, runIndex, options.maxStepsPolicy)
        }
    }

    val json = Json { prettyPrint = true }
    val logBody = records.joinToString("\n") { json.encodeToString(it) } + "\n"
    Files.writeString(logFile, logBody)

    val resultsMd = buildResultsMarkdown(records, options, logFile, root)
    root.resolve("eval/results.md").writeText(resultsMd)

    println("Eval complete: ${records.size} runs")
    println("Logs: $logFile")
    println("Report: ${root.resolve("eval/results.md")}")
}

private suspend fun runStepAgent(
    mcp: McpClient,
    seed: Long,
    runIndex: Int,
    maxSteps: Int,
): EvalRecord {
    val config = heuristicAgentConfig(maxSteps)
    val loop = AgentLoop(config = config, mcpFactory = { _ -> mcp })
    val started = System.currentTimeMillis()
    val result = loop.run(AgentRunRequest(seed = seed, maxSteps = maxSteps))
    return EvalRecord(
        agent = "step-agent",
        seed = seed,
        run = runIndex + 1,
        success = result.success,
        steps = result.stepsUsed,
        tokens = result.tokensUsed,
        finalHp = result.finalHp,
        finalPhase = result.finalPhase,
        durationMs = System.currentTimeMillis() - started,
        llmProvider = "heuristic",
    )
}

private suspend fun runPolicyAgent(
    mcp: McpClient,
    seed: Long,
    runIndex: Int,
    maxSteps: Int,
): EvalRecord {
    val config = heuristicPolicyConfig(maxSteps)
    val loop = PolicyAgentLoop(config = config, mcpFactory = { _ -> mcp })
    val started = System.currentTimeMillis()
    val result = loop.run(PolicyRunRequest(seed = seed, maxSteps = maxSteps))
    return EvalRecord(
        agent = "policy-agent",
        seed = seed,
        run = runIndex + 1,
        success = result.success,
        steps = result.stepsUsed,
        tokens = result.tokensUsed,
        finalHp = result.finalHp,
        finalPhase = result.finalPhase,
        durationMs = System.currentTimeMillis() - started,
        llmProvider = "heuristic",
    )
}

private fun heuristicAgentConfig(maxSteps: Int) = AgentConfig(
    llmProvider = "heuristic",
    llmApiKey = null,
    yandexFolderId = null,
    ollamaBaseUrl = "http://localhost:11434",
    ollamaModel = "qwen2.5:3b",
    ollamaFallbackModel = "qwen2.5:1.5b",
    llmRequestTimeoutMs = 120_000L,
    llmFallbackTimeoutMs = 45_000L,
    useHeuristicFallback = false,
    mcpCommand = emptyList(),
    mcpTransport = "http",
    mcpServerUrl = "http://localhost:8081",
    maxToolCalls = maxSteps,
    retryAttempts = 1,
    llmMaxHistoryMessages = 16,
    ollamaNumCtx = 8192,
    ollamaNumPredict = 256,
)

private fun heuristicPolicyConfig(maxSteps: Int) = PolicyAgentConfig(
    agent = heuristicAgentConfig(maxSteps),
    maxToolCalls = maxSteps,
    stuckThreshold = 3,
    replanEverySteps = 40,
    stuckReplanCooldown = 15,
    noProgressSteps = 25,
    strictFairPlay = false,
    requireLlm = false,
)

private data class EvalOptions(
    val runsPerSeed: Int,
    val seeds: List<Long>,
    val maxStepsStep: Int,
    val maxStepsPolicy: Int,
    val fromLog: Path? = null,
) {
    companion object {
        fun parse(args: Array<String>): EvalOptions {
            var runs = 5
            var seeds = listOf(41L, 42L, 43L, 44L, 45L)
            var maxStep = 1500
            var maxPolicy = 5000
            var fromLog: Path? = null
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--runs" -> runs = args.getOrNull(++i)?.toInt() ?: runs
                    "--seeds" -> seeds = args.getOrNull(++i)?.split(",")?.map { it.trim().toLong() } ?: seeds
                    "--max-steps-step" -> maxStep = args.getOrNull(++i)?.toInt() ?: maxStep
                    "--max-steps-policy" -> maxPolicy = args.getOrNull(++i)?.toInt() ?: maxPolicy
                    "--from-log" -> fromLog = Path.of(args.getOrNull(++i) ?: error("--from-log requires path"))
                }
                i++
            }
            return EvalOptions(runs, seeds, maxStep, maxPolicy, fromLog)
        }
    }
}

@Serializable
private data class EvalRecord(
    val agent: String,
    val seed: Long,
    val run: Int,
    val success: Boolean,
    val steps: Int,
    val tokens: Int,
    val finalHp: Int?,
    val finalPhase: String?,
    val durationMs: Long,
    val llmProvider: String,
)

private data class AgentSummary(
    val agent: String,
    val total: Int,
    val wins: Int,
    val winRate: Double,
    val avgSteps: Double,
    val avgTokens: Double,
    val avgFinalHp: Double?,
)

private fun buildResultsMarkdown(
    records: List<EvalRecord>,
    options: EvalOptions,
    logFile: Path,
    root: Path,
): String {
    val summaries = records.groupBy { it.agent }.map { (agent, rows) ->
        val wins = rows.count { it.success }
        val completed = rows.filter { it.success }
        AgentSummary(
            agent = agent,
            total = rows.size,
            wins = wins,
            winRate = wins.toDouble() / rows.size * 100.0,
            avgSteps = rows.map { it.steps }.average(),
            avgTokens = rows.map { it.tokens }.average(),
            avgFinalHp = completed.mapNotNull { it.finalHp }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    val chart = summaries.joinToString("\n") { s ->
        val bar = "█".repeat((s.winRate / 5).toInt().coerceAtMost(20))
        "| ${s.agent} | $bar ${"%.0f".format(s.winRate)}% |"
    }

    val table = buildString {
        appendLine("| Агент | Прогонов | Побед | % побед | Ср. шаги | Ср. токены | Ср. HP (победа) |")
        appendLine("|-------|----------|-------|---------|----------|------------|-----------------|")
        summaries.forEach { s ->
            val hp = s.avgFinalHp?.let { "%.0f".format(it) } ?: "—"
            appendLine(
                "| ${s.agent} | ${s.total} | ${s.wins} | ${"%.1f".format(s.winRate)}% | " +
                    "${"%.0f".format(s.avgSteps)} | ${"%.0f".format(s.avgTokens)} | $hp |",
            )
        }
    }

    val detail = detailTable(records)

    return """
# Eval results — Roguelike agents (hw2)

Сгенерировано: `${Instant.now()}`

Параметры прогона:
- Сиды: ${options.seeds.joinToString()}
- Прогонов на сид/агента: ${options.runsPerSeed}
- Step-agent maxSteps: ${options.maxStepsStep}
- Policy-agent maxSteps: ${options.maxStepsPolicy}
- Режим: `heuristic` (воспроизводимый baseline без LLM API)
- Логи: `${root.relativize(logFile)}`

## Сводка

$table

## % успешных прохождений (ASCII)

| Агент | Win rate |
|-------|----------|
$chart

## Детали прогонов

| Агент | Seed | Run | OK | Шаги | Токены | HP | Фаза |
|-------|------|-----|----|------|--------|----|------|
$detail

## Как повторить

**In-process (без Docker, быстрый baseline):**

```bash
./gradlew :eval:run --args="--runs 5 --seeds 41,42,43,44,45"
```

**Против поднятого Docker (оба агента, Ollama / Yandex):**

```bash
docker compose --profile policy-agent --profile llm up -d
./scripts/run-eval.sh
```

**Только policy-agent на Ollama** (step-agent eval — отдельно):

```bash
docker compose --profile policy-agent --profile llm up -d
./scripts/run-eval-policy.sh
```

Переменные: `EVAL_RUNS`, `EVAL_SEEDS`, `EVAL_MAX_STEPS_POLICY`, `POLICY_AGENT_URL`.

## Lessons learned

- **Policy DSL** стабильнее на длинных прогонах: меньше LLM-вызовов, предсказуемый micro-слой.
- **Step-agent** гибче в нестандартных ситуациях, но дороже по шагам/токенам при реальном LLM.
- Одинаковый `seed` в `game_new_session` даёт воспроизводимую карту для честного сравнения.

""".trimIndent() + "\n"
}

private fun regenerateFromLog(logPath: Path) {
    val json = Json { ignoreUnknownKeys = true }
    val records = Files.readAllLines(logPath)
        .filter { it.isNotBlank() }
        .map { line -> json.decodeFromString<EvalRecord>(line) }
    val root = Path.of(".").toAbsolutePath().normalize()
    val options = EvalOptions(
        runsPerSeed = records.maxOfOrNull { it.run } ?: 5,
        seeds = records.map { it.seed }.distinct().sorted(),
        maxStepsStep = 500,
        maxStepsPolicy = 2000,
    )
    val resultsMd = buildResultsMarkdown(records, options, logPath, root)
    root.resolve("eval/results.md").writeText(resultsMd)
    println("Regenerated ${root.resolve("eval/results.md")} from $logPath (${records.size} rows)")
}

private fun detailTable(records: List<EvalRecord>): String =
    records.joinToString("\n") { r ->
        "| ${r.agent} | ${r.seed} | ${r.run} | ${if (r.success) "✓" else "✗"} | ${r.steps} | ${r.tokens} | ${r.finalHp ?: "—"} | ${r.finalPhase} |"
    }

private object EvalStack {
    data class Running(val registry: McpToolRegistry)

    fun start(): Running {
        val engine = GameEngine()
        val port = object : GameSessionPort {
            override suspend fun createSession(seed: Long?, twoLevel: Boolean, coopAgent: Boolean): GameSnapshot =
                engine.createSession(seed, twoLevel, coopAgent)

            override suspend fun observe(sessionId: String): GameSnapshot =
                engine.getSnapshot(sessionId) ?: error("Session not found: $sessionId")

            override suspend fun applyAction(sessionId: String, action: String, actor: String): PlayerActionResponse =
                engine.applyAction(sessionId, action, actor)?.response
                    ?: PlayerActionResponse(accepted = false, message = "Session not found")

            override suspend fun sync(sessionId: String, input: InputSyncRequest, actor: String): PlayerActionResponse =
                engine.syncInput(sessionId, input.copy(actor = actor))?.response
                    ?: PlayerActionResponse(accepted = false, message = "Session not found")
        }
        return Running(McpToolRegistry(port))
    }
}

private class EvalMcpClient(
    private val registry: McpToolRegistry,
) : McpClient {
    override suspend fun callTool(name: String, arguments: Map<String, JsonElement>): McpToolResult {
        val response = registry.invoke(name, arguments)
        return McpToolResult(
            text = response.content.firstOrNull()?.text ?: "",
            isError = response.isError,
        )
    }

    override suspend fun getTools(): List<McpTool> =
        registry.descriptors().map { descriptor ->
            McpTool(
                name = descriptor.name,
                description = descriptor.description,
                inputSchema = descriptor.inputSchema,
            )
        }

    override fun close() = Unit
}
