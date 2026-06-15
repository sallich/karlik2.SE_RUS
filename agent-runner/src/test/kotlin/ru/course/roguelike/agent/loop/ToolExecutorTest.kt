package ru.course.roguelike.agent.loop

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.course.roguelike.agent.LLMMessage
import ru.course.roguelike.agent.TestSnapshots
import ru.course.roguelike.agent.llm.AgentDecisionClient
import ru.course.roguelike.agent.llm.HeuristicDecisionClient
import ru.course.roguelike.agent.mcp.McpClient
import ru.course.roguelike.agent.mcp.MockMcpClient
import ru.course.roguelike.agent.planner.KeyHuntPlanner
import ru.course.roguelike.agent.planner.ToolCallDecision
import ru.course.roguelike.shared.mcp.McpTool
import kotlin.test.assertFalse

class ToolExecutorTest {
    private lateinit var mcp: McpClient
    private lateinit var fallback: AgentDecisionClient
    private lateinit var conversation: MutableList<LLMMessage>
    private lateinit var lastPositions: ArrayDeque<Pair<Int, Int>>
    private lateinit var lastActionKeys: ArrayDeque<String>
    private lateinit var executor: ToolExecutor

    private val sessionId = "test-session"
    private val actor = KeyHuntPlanner.ACTOR_PLAYER
    private val budget = 10
    private val tools = listOf<McpTool>() // не используются в тестах, можно пустой

    @BeforeEach
    fun setUp() {
        mcp = MockMcpClient()
        fallback = HeuristicDecisionClient()
        conversation = mutableListOf()
        lastPositions = ArrayDeque()
        lastActionKeys = ArrayDeque()
        executor = ToolExecutor(
            mcp = mcp,
            fallback = fallback,
            conversation = conversation,
            lastPositions = lastPositions,
            lastActionKeys = lastActionKeys,
            tools = tools,
            sessionId = sessionId,
            actor = actor,
            toolLog = mutableListOf(),
            budget = budget
        )
    }

    @Test
    fun `simple call tool`() = runTest {
        val snapshot = TestSnapshots.simpleRoom()
        val decision = wait
        val result = executor.executeDecision(decision, snapshot)
        assertNotNull(result)
        assertFalse(result.shouldStop)
    }

    @Test
    fun `simple call tools`() = runTest {
        val snapshot = TestSnapshots.simpleRoom()
        val decision = wait
        val decisions = listOf(decision, decision)
        val result = executor.executeAll(decisions, 2, snapshot)
        assertEquals(1, result.stepsUsed)
        assertFalse(result.shouldStop)
    }

    @Test
    fun `detectLoop returns true when position unchanged for 3 steps`(): Unit = runTest {
        val snapshot = TestSnapshots.simpleRoom()
        val decision = moveEast

        lastPositions.addAll(listOf(2 to 2, 2 to 2, 2 to 2))

        val result = executor.detectLoop(snapshot, decision)
        assertTrue(result)
    }

    @Test
    fun `detectLoop returns true when same action repeated 4 times`() = runTest {
        val snapshot = TestSnapshots.simpleRoom()
        val decision = moveNorth

        val actionKey = buildActionKey(decision)
        repeat(9) { lastActionKeys.addLast(actionKey) }

        val result = executor.detectLoop(snapshot, decision)
        assertTrue(result)
        assertTrue(lastActionKeys.size == 10)
    }

    private fun buildActionKey(decision: ToolCallDecision): String {
        val filtered = decision.arguments.filterNot { it.key == "sessionId" || it.key == "actor" }
        return "${decision.tool}:$filtered"
    }

    companion object {

        private val wait = ToolCallDecision(
            tool = "game_act",
            arguments = buildJsonObject {
                put("action", JsonPrimitive("wait"))
            }.mapValues { it.value },
        )
        private val moveEast = ToolCallDecision(
            tool = "game_act",
            arguments = buildJsonObject {
                put("action", JsonPrimitive("move_east"))
            }.mapValues { it.value },
        )
        private val moveNorth = ToolCallDecision(
            tool = "game_act",
            arguments = buildJsonObject {
                put("action", JsonPrimitive("move_north"))
            }.mapValues { it.value },
        )
    }
}
