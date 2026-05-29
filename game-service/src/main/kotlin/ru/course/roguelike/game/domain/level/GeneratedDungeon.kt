package ru.course.roguelike.game.domain.level

/**
 * Многоуровневая локация: набор уровней, связанных тайлами-лифтами,
 * расположенными в одинаковых координатах на каждом уровне (issue #3).
 * Герой начинает на [levels].first().
 */
data class GeneratedDungeon(
    val levels: List<GeneratedLevel>,
)
