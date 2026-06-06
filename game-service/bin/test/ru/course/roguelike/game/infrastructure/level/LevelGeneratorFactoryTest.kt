package ru.course.roguelike.game.infrastructure.level

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LevelGeneratorFactoryTest {
    @Test
    fun `default generator is the labyrinth`() {
        assertSame(LabyrinthLevelGenerator, LevelGeneratorFactory.create())
    }

    @Test
    fun `labyrinth kind resolves to labyrinth generator`() {
        assertSame(LabyrinthLevelGenerator, LevelGeneratorFactory.create(LevelGeneratorFactory.LABYRINTH))
    }

    @Test
    fun `test kind resolves to test generator`() {
        assertSame(TestLevelGenerator, LevelGeneratorFactory.create(LevelGeneratorFactory.TEST))
    }

    @Test
    fun `unknown kind falls back to labyrinth generator`() {
        assertSame(LabyrinthLevelGenerator, LevelGeneratorFactory.create("does-not-exist"))
    }
}
