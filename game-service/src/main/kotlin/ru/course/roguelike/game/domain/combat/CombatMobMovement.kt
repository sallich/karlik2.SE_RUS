package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import kotlin.math.hypot

internal object CombatMobMovement {
    fun moveToward(
        map: TileMap,
        mob: MobEntity,
        targetX: Float,
        targetY: Float,
        speed: Float,
        deltaSec: Float,
    ) {
        val dx = targetX - mob.x
        val dy = targetY - mob.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < 0.01f) return
        val step = speed * deltaSec
        val moveX = dx / dist * step
        val moveY = dy / dist * step
        val circle = EntityCollision.Circle(mob.x, mob.y, CombatConstants.MOB_RADIUS)
        val moved = EntityCollision.moveWithWallSlide(map, circle, moveX, moveY, localHeight = mob.z)
        mob.x = moved.x
        mob.y = moved.y
    }

    fun strafeAround(
        map: TileMap,
        mob: MobEntity,
        playerX: Float,
        playerY: Float,
        speed: Float,
        deltaSec: Float,
    ) {
        val dx = playerX - mob.x
        val dy = playerY - mob.y
        val sign = if (mob.id % 2L == 0L) 1f else -1f
        val perpX = -dy * sign
        val perpY = dx * sign
        val len = hypot(perpX.toDouble(), perpY.toDouble()).toFloat().coerceAtLeast(0.001f)
        moveToward(map, mob, mob.x + perpX / len, mob.y + perpY / len, speed * 0.75f, deltaSec)
    }

    fun separateMobs(mobs: List<MobEntity>, map: TileMap) {
        val minDist = CombatConstants.MOB_SEPARATION_DISTANCE
        for (i in mobs.indices) {
            for (j in i + 1 until mobs.size) {
                separatePair(mobs[i], mobs[j], map, minDist)
            }
        }
        mobs.filter { it.alive }.forEach { clampMobOutOfWalls(map, it) }
    }

    private fun separatePair(a: MobEntity, b: MobEntity, map: TileMap, minDist: Float) {
        if (!a.alive || !b.alive) return
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist >= minDist) return
        val push = (minDist - dist) / 2f
        if (dist < 0.001f) {
            nudgeMob(map, a, -0.15f, 0f)
            nudgeMob(map, b, 0.15f, 0f)
        } else {
            val nx = dx / dist
            val ny = dy / dist
            nudgeMob(map, a, -nx * push, -ny * push)
            nudgeMob(map, b, nx * push, ny * push)
        }
    }

    private fun nudgeMob(map: TileMap, mob: MobEntity, dx: Float, dy: Float) {
        val circle = EntityCollision.Circle(mob.x, mob.y, CombatConstants.MOB_RADIUS)
        val moved = EntityCollision.moveWithWallSlide(map, circle, dx, dy, localHeight = mob.z)
        mob.x = moved.x
        mob.y = moved.y
    }

    fun clampMobOutOfWalls(map: TileMap, mob: MobEntity) {
        val radius = CombatConstants.MOB_RADIUS
        repeat(6) {
            val circle = EntityCollision.Circle(mob.x, mob.y, radius)
            if (!EntityCollision.overlapsMovement(map, circle, mob.z)) return
            for ((dx, dy) in nudges) {
                val moved = EntityCollision.moveWithWallSlide(
                    map,
                    circle,
                    dx,
                    dy,
                    localHeight = mob.z,
                )
                if (!EntityCollision.overlapsMovement(map, moved, mob.z)) {
                    mob.x = moved.x
                    mob.y = moved.y
                    return
                }
            }
        }
    }

    private val nudges = arrayOf(
        0.14f to 0f,
        -0.14f to 0f,
        0f to 0.14f,
        0f to -0.14f,
        0.1f to 0.1f,
        -0.1f to 0.1f,
        0.1f to -0.1f,
        -0.1f to -0.1f,
    )
}
