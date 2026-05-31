package ru.course.roguelike.game.domain.combat

import ru.course.roguelike.game.domain.ai.MobDecisionContext
import ru.course.roguelike.game.domain.ai.MobIntent
import ru.course.roguelike.game.domain.ai.distanceTo
import ru.course.roguelike.game.domain.event.GameEvent
import ru.course.roguelike.game.domain.progression.ProgressionSystem
import ru.course.roguelike.game.domain.session.GameSession
import ru.course.roguelike.shared.engine.EntityCollision
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.CombatConstants
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
        }
        tickMobs(session, map, deltaSec, events)
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

        for (mob in session.mobs) {
            if (!mob.alive) continue
            val distance = mob.distanceTo(playerX, playerY)
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
        val playerCircle = EntityCollision.playerCircle(session.playerPose)
        session.projectiles.removeAll { projectile ->
            projectile.x += projectile.velocityX * deltaSec
            projectile.y += projectile.velocityY * deltaSec
            val circle = EntityCollision.Circle(
                projectile.x,
                projectile.y,
                CombatConstants.PROJECTILE_RADIUS,
            )
            when {
                EntityCollision.overlapsWall(map, circle) -> true
                projectile.fromPlayer -> handlePlayerProjectileHit(session, circle, events)
                EntityCollision.circlesOverlap(circle, playerCircle) -> {
                    damagePlayer(session, projectile.damage, events)
                    true
                }
                else -> false
            }
        }
    }

    private fun handlePlayerProjectileHit(
        session: GameSession,
        circle: EntityCollision.Circle,
        events: MutableList<GameEvent>,
    ): Boolean {
        val hitMob = session.mobs.firstOrNull { mob ->
            mob.alive &&
                EntityCollision.circlesOverlap(
                    circle,
                    EntityCollision.Circle(mob.x, mob.y, CombatConstants.MOB_RADIUS),
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
        if (session.playerAttackCooldownMs > 0) return null
        session.projectiles.add(
            ProjectileEntity.fromPlayer(
                session.playerPose,
                session.allocateEntityId(),
                session.playerAttackDamage,
            ),
        )
        session.playerAttackCooldownMs = CombatConstants.PLAYER_ATTACK_COOLDOWN_MS
        return null
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
        val moved = EntityCollision.moveWithWallSlide(map, circle, moveX, moveY)
        mob.x = moved.x
        mob.y = moved.y
    }

    private const val MILLIS_PER_SECOND = 1000f
}
