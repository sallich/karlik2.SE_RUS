package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.shared.model.GridPos
import kotlin.math.hypot
import kotlin.random.Random

internal object MobSpawnCells {
    private const val MIN_MOB_SEPARATION = 2.5f
    private const val LOOSE_MOB_SEPARATION = 1.4f

    fun pick(
        candidates: List<GridPos>,
        count: Int,
        random: Random,
        avoid: List<GridPos> = emptyList(),
    ): List<GridPos> {
        val pool = candidates.shuffled(random)
        val spaced = selectSpaced(pool, count, avoid)
        if (spaced.size >= count) return spaced
        val fallback = pool.filter { it !in spaced && isFarEnoughFrom(it, spaced + avoid, LOOSE_MOB_SEPARATION) }
        return spaced + fallback.take(count - spaced.size)
    }

    private fun selectSpaced(pool: List<GridPos>, count: Int, avoid: List<GridPos>): List<GridPos> {
        val picked = mutableListOf<GridPos>()
        for (pos in pool) {
            if (picked.size == count) return picked
            if (isFarEnoughFrom(pos, picked + avoid)) picked.add(pos)
        }
        return picked
    }

    private fun isFarEnoughFrom(
        pos: GridPos,
        others: List<GridPos>,
        minSeparation: Float = MIN_MOB_SEPARATION,
    ): Boolean {
        val wx = pos.x + 0.5f
        val wy = pos.y + 0.5f
        return others.all { other ->
            hypot(
                (other.x + 0.5f - wx).toDouble(),
                (other.y + 0.5f - wy).toDouble(),
            ) >= minSeparation
        }
    }
}
