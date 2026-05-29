package ru.course.roguelike.game.domain.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.infrastructure.level.TestLevelGenerator
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.protocol.GameActions

class CommandRegistryTest {
    private val session = run {
        val level = TestLevelGenerator.generate(1L)
        GameSession(
            sessionId = "s1",
            seed = 1L,
            map = level.map,
            playerPose = PlayerPose.fromGridCell(level.playerSpawn),
        )
    }

    @Test
    fun `default registry resolves movement actions`() {
        val registry = CommandRegistry.default()
        assertEquals(GameActions.MOVEMENT, registry.knownActions())
        val cmd = registry.commandFor(GameActions.MOVE_NORTH, session)
        assertNotNull(cmd)
        assertEquals(GameActions.MOVE_NORTH, cmd!!.name)
    }

    @Test
    fun `unknown action returns null`() {
        val registry = CommandRegistry.default()
        assertNull(registry.commandFor("attack", session))
    }

    @Test
    fun `custom action can be registered in builder`() {
        val registry = CommandRegistry.defaultBuilder()
            .register("ping") { object : GameCommand {
                override val name = "ping"
                override fun validate(session: GameSession) = CommandValidation(true)
                override fun execute(session: GameSession) =
                    CommandExecutionResult(true, "pong")
            } }
            .build()

        assertTrue("ping" in registry.knownActions())
        assertEquals("ping", registry.commandFor("ping", session)?.name)
    }
}
