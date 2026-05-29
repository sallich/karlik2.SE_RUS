package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.LevelGenerator

object LevelGeneratorFactory {
    fun create(kind: String = DEFAULT_KIND): LevelGenerator = when (kind) {
        "test" -> TestLevelGenerator
        else -> TestLevelGenerator
    }

    const val DEFAULT_KIND = "test"
}
