package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.MobDecisionContext
import ru.course.roguelike.game.domain.ai.MobIntent
import ru.course.roguelike.game.domain.ai.distanceTo
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.inventory.InventoryWeapons
import ru.course.roguelike.game.domain.progression.ProgressionSystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.game.domain.session.MobLootDropper
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
import ru.course.roguelike.shared.model.InventoryDefinitions
import ru.course.roguelike.shared.model.InventoryItemType
import kotlin.math.hypot

object CombatSystem {
    fun tick(session: GameSession, deltaMs: Int, playerAttacking: Boolean): List<GameEvent> {
        if (session.playerHp <= 0) return emptyList()

        val events = mutableListOf<GameEvent>()
        val deltaSec = deltaMs / MILLIS_PER_SECOND
        val map = session.activeMap

        tickCooldowns(session, deltaMs)
        if (playerAttacking) {
            firePlayerProjectile(session)?.let { events.add(it) }
            events.addAll(InventoryWeapons.tryAutoReload(session))
        }
        tickMobs(session, map, deltaSec, events)
        session.mobs.filter { it.alive }.forEach { clampMobOutOfWalls(map, it) }
        separateMobs(session.mobs, map)
        tickProjectiles(session, map, deltaSec, events)
        removeDeadMobs(session, events)
        events.addAll(ProgressionSystem.checkLocationCompletion(session))
        return events
    }

    private fun tickCooldowns(session: GameSession, deltaMs: Int) {
        session.playerAttackCooldownMs = (session.playerAttackCooldownMs - deltaMs).coerceAtLeast(0)
        session.mobs.forEach { mob ->
            mob.attackCooldownMs = (mob.attackCooldownMs - deltaMs).coerceAtLeast(0)
        }
    }

    private fun tickMobs(
        session: GameSession,
        map: TileMap,
        deltaSec: Float,
        events: MutableList<GameEvent>,
    ) {
        val playerX = session.playerPose.x
        val playerY = session.playerPose.y
        val playerZ = EntityCollision.playerHitCenterZ(session.playerPose)

        for (mob in session.mobs) {
            if (!mob.alive) continue

            val reinforceTarget = mob.reinforceTarget
            if (reinforceTarget != null) {
                val playerInTarget = reinforceTarget.containsWorld(playerX, playerY)
                val mobInTarget = reinforceTarget.containsWorld(mob.x, mob.y)
                if (!playerInTarget && !mobInTarget) {
                    moveToward(
                        map,
                        mob,
                        reinforceTarget.center.x + 0.5f,
                        reinforceTarget.center.y + 0.5f,
                        mob.moveSpeed,
                        deltaSec,
                    )
                    continue
                }
                if (!playerInTarget) continue
            }

            val distance = mob.distanceTo(playerX, playerY)
            val canEngage = mob.aggroRoom.containsWorld(playerX, playerY) ||
                (reinforceTarget != null && reinforceTarget.containsWorld(playerX, playerY))
            if (!canEngage) continue

            val context = MobDecisionContext(
                mob = mob,
                playerX = playerX,
                playerY = playerY,
                distanceToPlayer = distance,
                playerHp = session.playerHp,
            )
            when (mob.behavior.decide(context)) {
                MobIntent.Idle -> Unit
                MobIntent.ChasePlayer -> moveToward(map, mob, playerX, playerY, mob.moveSpeed, deltaSec)
                MobIntent.KitePlayer -> moveToward(map, mob, playerX, playerY, -mob.moveSpeed, deltaSec)
                MobIntent.StrafePlayer -> strafeAround(map, mob, playerX, playerY, mob.moveSpeed, deltaSec)
                MobIntent.AttackPlayer -> {
                    if (distance <= mob.attackRange && mob.attackCooldownMs <= 0) {
                        damagePlayer(session, mob.attackDamage, events)
                        mob.attackCooldownMs = mob.attackCooldownTotalMs
                    }
                }
                MobIntent.ShootPlayer -> {
                    if (mob.attackCooldownMs <= 0) {
                        session.projectiles.add(
                            ProjectileEntity.fromMob(
                                mob,
                                playerX,
                                playerY,
                                playerZ,
                                session.allocateEntityId(),
                            ),
                        )
                        mob.attackCooldownMs = mob.attackCooldownTotalMs
                    }
                }
            }
        }
    }

    private fun tickProjectiles(
        session: GameSession,
        map: TileMap,
        deltaSec: Float,
        events: MutableList<GameEvent>,
    ) {
        val playerZ = EntityCollision.playerHitCenterZ(session.playerPose)
        session.projectiles.removeAll { projectile ->
            projectile.x += projectile.velocityX * deltaSec
            projectile.y += projectile.velocityY * deltaSec
            projectile.z += projectile.velocityZ * deltaSec
            val circle = EntityCollision.Circle(
                projectile.x,
                projectile.y,
                CombatConstants.PROJECTILE_RADIUS,
            )
            when {
                EntityCollision.overlapsWall(map, circle, worldZ = projectile.z) -> true
                projectile.fromPlayer -> handlePlayerProjectileHit(session, projectile, events)
                EntityCollision.spheresOverlap3D(
                    projectile.x,
                    projectile.y,
                    projectile.z,
                    CombatConstants.PROJECTILE_RADIUS,
                    session.playerPose.x,
                    session.playerPose.y,
                    playerZ,
                    CombatConstants.MOB_HIT_HALF_HEIGHT,
                ) -> {
                    damagePlayer(session, projectile.damage, events)
                    true
                }
                else -> false
            }
        }
    }

    private fun handlePlayerProjectileHit(
        session: GameSession,
        projectile: ProjectileEntity,
        events: MutableList<GameEvent>,
    ): Boolean {
        val hitMob = session.mobs.firstOrNull { mob ->
            mob.alive &&
                EntityCollision.spheresOverlap3D(
                    projectile.x,
                    projectile.y,
                    projectile.z,
                    CombatConstants.PROJECTILE_RADIUS,
                    mob.x,
                    mob.y,
                    mob.hitCenterZ(),
                    CombatConstants.MOB_HIT_HALF_HEIGHT,
                )
        } ?: return false

        val before = hitMob.hp
        hitMob.hp = (before - session.playerAttackDamage).coerceAtLeast(0)
        val dealt = before - hitMob.hp
        events.add(
            if (hitMob.hp <= 0) {
                onMobKilled(session, hitMob, events)
                GameEvent.MobKilled(hitMob.id)
            } else {
                GameEvent.MobDamaged(hitMob.id, dealt, hitMob.hp)
            },
        )
        return true
    }

    private fun firePlayerProjectile(session: GameSession): GameEvent? {
        val weaponType = InventoryWeapons.equippedWeaponType(session) ?: InventoryItemType.PISTOL
        val ammoCost = InventoryDefinitions.ammoCost(weaponType)
        if (session.playerAttackCooldownMs > 0) return null
        if (session.playerAmmo < ammoCost) return null

        val totalDamage = session.playerAttackDamage
        val pelletCount = InventoryDefinitions.pelletCount(weaponType)
        val spread = InventoryDefinitions.spreadRadians(weaponType)
        val pelletDmg = InventoryDefinitions.pelletDamage(weaponType, totalDamage)
        repeat(pelletCount) { index ->
            val offset = if (pelletCount <= 1) {
                0f
            } else {
                spread * ((index.toFloat() / (pelletCount - 1)) - 0.5f) * 2f
            }
            session.projectiles.add(
                ProjectileEntity.fromPlayer(
                    session.playerPose,
                    session.allocateEntityId(),
                    pelletDmg,
                    yawOffset = offset,
                ),
            )
        }
        session.playerAttackCooldownMs = InventoryDefinitions.attackCooldownMs(weaponType)
        session.playerAmmo -= ammoCost
        session.equippedWeaponItemId?.let { session.weaponLoadedAmmo[it] = session.playerAmmo }
        return GameEvent.AmmoChanged(-ammoCost, session.playerAmmo)
    }

    private fun damagePlayer(session: GameSession, amount: Int, events: MutableList<GameEvent>) {
        val before = session.playerHp
        session.playerHp = (before - amount).coerceAtLeast(0)
        events.add(GameEvent.PlayerDamaged(before - session.playerHp, session.playerHp))
    }

    private fun removeDeadMobs(session: GameSession, events: MutableList<GameEvent>) {
        val dead = session.mobs.filter { !it.alive }
        if (dead.isEmpty()) return
        dead.forEach { mob ->
            if (events.none { it is GameEvent.MobKilled && it.mobId == mob.id }) {
                onMobKilled(session, mob, events)
                events.add(GameEvent.MobKilled(mob.id))
            }
        }
        session.mobs.removeAll { !it.alive }
    }

    private fun onMobKilled(session: GameSession, mob: MobEntity, events: MutableList<GameEvent>) {
        events.addAll(ProgressionSystem.awardMobKill(session, mob.kind))
        MobLootDropper.dropFrom(session, mob)?.let { events.add(it) }
    }

    private fun moveToward(
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

    private fun strafeAround(
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

    private fun separateMobs(mobs: List<MobEntity>, map: TileMap) {
        val minDist = CombatConstants.MOB_SEPARATION_DISTANCE
        for (i in mobs.indices) {
            for (j in i + 1 until mobs.size) {
                val a = mobs[i]
                val b = mobs[j]
                if (!a.alive || !b.alive) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist >= minDist) continue
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
        }
        mobs.filter { it.alive }.forEach { clampMobOutOfWalls(map, it) }
    }

    private fun nudgeMob(map: TileMap, mob: MobEntity, dx: Float, dy: Float) {
        val circle = EntityCollision.Circle(mob.x, mob.y, CombatConstants.MOB_RADIUS)
        val moved = EntityCollision.moveWithWallSlide(map, circle, dx, dy, localHeight = mob.z)
        mob.x = moved.x
        mob.y = moved.y
    }

    private fun clampMobOutOfWalls(map: TileMap, mob: MobEntity) {
        val radius = CombatConstants.MOB_RADIUS
        repeat(6) {
            val circle = EntityCollision.Circle(mob.x, mob.y, radius)
            if (!EntityCollision.overlapsMovement(map, circle, mob.z)) return
            val nudges = arrayOf(
                0.14f to 0f,
                -0.14f to 0f,
                0f to 0.14f,
                0f to -0.14f,
                0.1f to 0.1f,
                -0.1f to 0.1f,
                0.1f to -0.1f,
                -0.1f to -0.1f,
            )
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

    private const val MILLIS_PER_SECOND = 1000f
}
