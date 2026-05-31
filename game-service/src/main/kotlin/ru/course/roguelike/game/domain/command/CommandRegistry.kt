package ru.course.roguelike.game.domain.command

import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.protocol.GameActions

class CommandRegistry private constructor(
    private val factories: Map<String, CommandFactory>,
) {
    fun commandFor(action: String, session: GameSession): GameCommand? =
        factories[action]?.create(session)

    fun knownActions(): Set<String> = factories.keys

    fun interface CommandFactory {
        fun create(session: GameSession): GameCommand
    }

    class Builder {
        private val factories = linkedMapOf<String, CommandFactory>()

        fun register(action: String, factory: CommandFactory): Builder = apply {
            factories[action] = factory
        }

        fun register(action: String, create: (GameSession) -> GameCommand): Builder =
            register(action, CommandFactory(create))

        fun build(): CommandRegistry = CommandRegistry(factories.toMap())
    }

    companion object {
        fun default(): CommandRegistry = defaultBuilder().build()

        fun defaultBuilder(): Builder = Builder().apply { registerDefaults() }

        private fun Builder.registerDefaults() {
            GameActions.ALL.forEach { action ->
                register(action) { LegacyMovementCommand(action) }
            }
        }
    }
}
