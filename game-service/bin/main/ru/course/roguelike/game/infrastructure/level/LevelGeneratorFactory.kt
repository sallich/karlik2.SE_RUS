package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.LevelGenerator

object LevelGeneratorFactory {
    /** Лабиринтная локация (issue #3) — основной генератор для игровых сессий. */
    const val LABYRINTH = "labyrinth"

    /** Простой прямоугольный зал — используется в быстрых детерминированных тестах. */
    const val TEST = "test"

    const val DEFAULT_KIND = LABYRINTH

    fun create(kind: String = DEFAULT_KIND): LevelGenerator = when (kind) {
        TEST -> TestLevelGenerator
        LABYRINTH -> LabyrinthLevelGenerator
        else -> LabyrinthLevelGenerator
    }
}
